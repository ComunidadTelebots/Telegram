"""Poll protected ready/* tags; build immutable revisions and publish once.

No GitHub webhook listener or public Docker API. First poll records a baseline.
Ambiguous Telegram uploads stay 'sending' for manual reconciliation, not retry.
"""
from build_resources import configuration as android_resources, docker_arguments
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import sqlite3
import subprocess
import time
from datetime import datetime, timezone
from urllib.parse import urlsplit

REPOS = {'android': 'ComunidadTelebots/Telegram', 'react': 'ComunidadTelebots/telegram-react'}
TAG = re.compile(r'^ready/(dev|alfa|beta|rc|stable)/([0-9]+\.[0-9]+\.[0-9]+(?:[.-][A-Za-z0-9]+)*)$')


def run(*args, cwd=None):
    return subprocess.check_output(args, cwd=cwd, stderr=subprocess.STDOUT, timeout=7200).decode().strip()


def target(config, product, channel):
    value = config.get(product, {}).get(channel, '')
    if not isinstance(value, str) or not re.fullmatch(r'@[A-Za-z][A-Za-z0-9_]{4,31}|-100[0-9]+', value):
        raise ValueError('Configure an explicit Telegram channel for ' + product + '/' + channel)
    return value


def public_record(product, channel, version, sha, digest, chat, message_id):
    if channel != 'stable':
        raise ValueError('Only stable may enter the public feed')
    return dict(product=product, channel='stable', version=version, commit=sha,
                sha256=digest, published_at=datetime.now(timezone.utc).isoformat(),
                download_url=f'https://t.me/{chat[1:]}/{message_id}' if chat.startswith('@') else None)


def android_metadata(artifact, channel, version):
    value = json.loads(artifact.with_name('application.metadata.json').read_text(encoding='utf-8'))
    if (value.get('package') != 'org.comunidadtelebots.' + channel or
            type(value.get('version_code')) is not int or not 0 < value['version_code'] <= 2100000000 or
            value.get('version_name') != version):
        raise ValueError('APK metadata does not match release')
    if ('size_bytes' in value and (type(value['size_bytes']) is not int or value['size_bytes'] != artifact.stat().st_size)
            or 'min_sdk' in value and (type(value['min_sdk']) is not int or not 1 <= value['min_sdk'] <= 1000)
            or 'abis' in value and (not isinstance(value['abis'], list) or len(value['abis']) > 16 or any(not isinstance(abi, str) or not re.fullmatch(r'[a-zA-Z0-9_-]{1,32}', abi) for abi in value['abis']))
            or 'signer_sha256' in value and (not isinstance(value['signer_sha256'], list) or not 1 <= len(value['signer_sha256']) <= 8 or any(not isinstance(cert, str) or not re.fullmatch(r'[a-f0-9]{64}', cert) for cert in value['signer_sha256']))
            or 'release_notes' in value and (not isinstance(value['release_notes'], str) or len(value['release_notes']) > 2000 or len(value['release_notes'].encode()) > 4000)):
        raise ValueError('Invalid APK compatibility or integrity metadata')
    value['size_bytes'] = artifact.stat().st_size
    return value


def release_table(db):
    db.execute('CREATE TABLE IF NOT EXISTS android_releases(tag TEXT PRIMARY KEY,record TEXT NOT NULL)')


def android_record(metadata, channel, chat, message_id, sha, checksum):
    if type(message_id) is not int or message_id <= 0:
        raise ValueError('Missing Telegram confirmation')
    if re.fullmatch(r'-100[0-9]+', chat):
        url = f'https://t.me/c/{chat[4:]}/{message_id}'
    elif re.fullmatch(r'@[A-Za-z][A-Za-z0-9_]{4,31}', chat):
        url = f'https://t.me/{chat[1:]}/{message_id}'
    else:
        raise ValueError('Invalid confirmed channel')
    return dict(metadata, product='android', channel=channel, url=url, commit=sha,
                sha256=checksum, confirmed=True, published_at=datetime.now(timezone.utc).isoformat())


def verify_channel(chat):
    token = Path('/run/secrets/bot_token').read_text().strip()
    from urllib.request import Request, urlopen
    def api(method, data):
        request = Request(os.environ['BOT_API_URL'].rstrip('/') + '/bot' + token + '/' + method,
                          data=json.dumps(data).encode(), headers={'Content-Type': 'application/json'})
        with urlopen(request, timeout=30) as response:
            value = json.load(response)
        if not value.get('ok'):
            raise ValueError('Bot API check failed')
        return value['result']
    bot = api('getMe', {})
    info = api('getChat', {'chat_id': chat})
    member = api('getChatMember', {'chat_id': chat, 'user_id': bot['id']})
    if info.get('type') != 'channel' or member.get('status') not in ('creator', 'administrator'):
        raise ValueError('Destination must be a channel administered by this bot')
    if member['status'] != 'creator' and not member.get('can_post_messages'):
        raise ValueError('Bot cannot post in destination channel')
    return '@' + info['username'] if info.get('username') else chat


def send_file(path, chat, caption, protected):
    token = Path('/run/secrets/bot_token').read_text().strip()
    if not re.fullmatch(r'[0-9]+:[A-Za-z0-9_-]+', token):
        raise ValueError('Invalid bot token file')
    endpoint = urlsplit(os.environ['BOT_API_URL'])
    if endpoint.scheme != 'https' and not (endpoint.scheme == 'http' and endpoint.hostname == 'bot-api'):
        raise ValueError('HTTPS required outside the internal bot-api service')
    if endpoint.username or endpoint.query or endpoint.fragment:
        raise ValueError('Invalid Bot API URL')
    boundary = 'telebots' + os.urandom(16).hex()
    chunks = []
    for key, value in {'chat_id': chat, 'caption': caption, 'protect_content': str(protected).lower()}.items():
        chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
    chunks.append(f'--{boundary}\r\nContent-Disposition: form-data; name="document"; filename="{path.name}"\r\nContent-Type: application/octet-stream\r\n\r\n'.encode())
    prefix = b''.join(chunks)
    suffix = f'\r\n--{boundary}--\r\n'.encode()
    connection = (http.client.HTTPSConnection if endpoint.scheme == 'https' else http.client.HTTPConnection)(endpoint.hostname, endpoint.port, timeout=600)
    try:
        connection.putrequest('POST', endpoint.path.rstrip('/') + '/bot' + token + '/sendDocument')
        connection.putheader('Content-Type', 'multipart/form-data; boundary=' + boundary)
        connection.putheader('Content-Length', str(len(prefix) + path.stat().st_size + len(suffix)))
        connection.endheaders()
        connection.send(prefix)
        with path.open('rb') as stream:
            while data := stream.read(1024 * 1024):
                connection.send(data)
        connection.send(suffix)
        response = connection.getresponse()
        payload = json.loads(response.read(1024 * 1024))
        if response.status != 200 or not payload.get('ok'):
            raise RuntimeError('Telegram rejected upload; inspect job before retry')
        return payload['result']['message_id']
    finally:
        connection.close()


def build(root, product, tag, object_sha, channel):
    resources = docker_arguments(android_resources()) if product == 'android' else ['--memory=3g', '--memory-swap=4g', '--cpus=1']
    job = hashlib.sha256((product + tag + object_sha).encode()).hexdigest()[:24]
    folder = root / job
    source, out = folder / 'source', folder / 'out'
    folder.mkdir(exist_ok=False)
    out.mkdir()
    run('git', 'clone', '--depth', '1', '--branch', tag, '--', 'https://github.com/' + REPOS[product] + '.git', str(source))
    # Detect a tag moved between discovery and clone, including annotated tags.
    if run('git', 'rev-parse', 'refs/tags/' + tag, cwd=source) != object_sha:
        raise ValueError('Tag changed after discovery')
    sha = run('git', 'rev-parse', 'HEAD', cwd=source)
    # Telegram's media and native sources are pinned Git submodules. A plain
    # clone does not contain them and fails before Gradle can configure builds.
    run('git', '-c', 'protocol.file.allow=never', 'submodule', 'update',
        '--init', '--recursive', '--depth', '1', cwd=source)
    command = ['docker', 'run', '--rm', '--name', 'telebots-build-' + job,
               *resources, '--security-opt=no-new-privileges',
               '-e', 'CHANNEL=' + channel, '-e', 'RELEASE_VERSION=' + tag.split('/')[-1],
               '-v', f'{source}:/src', '-v', f'{out}:/out']
    if product == 'android':
        command += ['-v', os.environ['SIGNING_DIR'] + ':/signing:ro']
    command += ['telebots-' + product + '-builder:local']
    # Build logs can contain app credentials: private work dir, never publish logs.
    with (folder / 'build.log').open('wb') as log:
        try:
            subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=7200)
        finally:
            subprocess.run(['docker', 'rm', '-f', 'telebots-build-' + job], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=30)
    path = out / ('application.apk' if product == 'android' else 'application.tar.gz')
    if not path.is_file() or not 0 < path.stat().st_size < 2_000_000_000:
        raise ValueError('Missing or oversized artifact')
    renamed = out / (product + '-' + channel + '-' + tag.split('/')[-1] + ('.apk' if product == 'android' else '.tar.gz'))
    path.rename(renamed)
    return renamed, sha


def discover(product):
    text = run('git', 'ls-remote', '--tags', '--refs', 'https://github.com/' + REPOS[product] + '.git', 'refs/tags/ready/*')
    result = {}
    for line in text.splitlines():
        sha, ref = line.split()
        tag = ref.removeprefix('refs/tags/')
        if TAG.fullmatch(tag) and re.fullmatch('[a-f0-9]{40}', sha):
            result[tag] = sha
    return result


def process(db, root, product, tag, object_sha, config):
    channel, version = TAG.fullmatch(tag).groups()
    if channel == 'stable' and not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version):
        raise ValueError('Stable requires a final version without prerelease suffixes')
    chat = target(config, product, channel)
    public_chat = verify_channel(chat)
    db.execute('INSERT INTO jobs VALUES (?,?,?,?,NULL)', (product, tag, object_sha, 'building'))
    db.commit()
    try:
        artifact, sha = build(root, product, tag, object_sha, channel)
        metadata = android_metadata(artifact, channel, version) if product == 'android' else None
        with artifact.open('rb') as stream:
            digest = hashlib.file_digest(stream, 'sha256').hexdigest()
        caption = f'{product} · {channel} · {version}\nCommit: {sha}\nSHA-256: {digest}'
        db.execute("UPDATE jobs SET state='sending' WHERE product=? AND tag=?", (product, tag))
        db.commit()
        # Installable packages must remain downloadable in their destination channel.
        message_id = send_file(artifact, chat, caption, False)
        record = public_record(product, channel, version, sha, digest, public_chat, message_id) if channel == 'stable' else None
        if metadata is not None:
            release_table(db)
            private = android_record(metadata, channel, public_chat, message_id, sha, digest)
            db.execute('INSERT INTO android_releases VALUES (?,?)', (tag, json.dumps(private)))
        db.execute("UPDATE jobs SET state='sent', record=? WHERE product=? AND tag=?", (json.dumps(record), product, tag))
        db.commit()
    except Exception as exc:
        # Never expose exception contents (may contain authenticated URLs).
        print(product, tag, 'requires review:', type(exc).__name__, flush=True)
        db.execute("UPDATE jobs SET state='failed' WHERE product=? AND tag=? AND state='building'", (product, tag))
        db.commit()


def write_public(db, destination):
    for product in REPOS:
        rows = db.execute("SELECT record FROM jobs WHERE product=? AND state='sent' AND record!='null'", (product,)).fetchall()
        if rows:
            record = max((json.loads(row[0]) for row in rows), key=lambda x: tuple(map(int, x['version'].split('.'))))
            if record.get('channel') != 'stable':
                raise ValueError('Non-stable public record rejected')
            temp = destination / (product + '.json.tmp')
            temp.write_text(json.dumps(record, ensure_ascii=False), encoding='utf-8')
            temp.chmod(0o644)
            temp.replace(destination / (product + '.json'))


def write_android_releases(db, destination):
    """Private volume, never the public website. Rebuilt from confirmed receipts."""
    release_table(db)
    rows = db.execute("SELECT r.record FROM android_releases r JOIN jobs j ON j.product='android' AND j.tag=r.tag WHERE j.state='sent'").fetchall()
    records = [json.loads(row[0]) for row in rows]
    folder = destination / 'android'
    folder.mkdir(parents=True, exist_ok=True)
    folder.chmod(0o755)  # Access service has a different UID; volume is never web-served.
    for channel in ('dev', 'alfa', 'beta', 'rc', 'stable'):
        candidates = [record for record in records if record['channel'] == channel and record.get('confirmed') is True]
        if not candidates:
            continue
        record = max(candidates, key=lambda r: r['version_code'])
        temporary = folder / (channel + '.json.tmp')
        temporary.write_text(json.dumps(record, ensure_ascii=False), encoding='utf-8')
        temporary.chmod(0o644)
        temporary.replace(folder / (channel + '.json'))


def main():
    os.umask(0o077)
    products = os.environ.get('PRODUCTS', 'android').split(',')
    if not products or any(product not in REPOS for product in products):
        raise ValueError('PRODUCTS must contain android and/or react')
    root = Path(os.environ['WORK_ROOT']).resolve()
    root.mkdir(parents=True, exist_ok=True)
    # One worker only, including across container restarts on the same machine.
    import fcntl
    lock = (root / 'worker.lock').open('w')
    fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    db = sqlite3.connect(root / 'state.sqlite')
    db.execute('CREATE TABLE IF NOT EXISTS jobs(product TEXT,tag TEXT,sha TEXT,state TEXT,record TEXT,PRIMARY KEY(product,tag))')
    db.execute('CREATE TABLE IF NOT EXISTS initialized(product TEXT PRIMARY KEY)')
    public = Path('/public')
    public.mkdir(exist_ok=True)
    public.chmod(0o755)
    while True:
        for product in products:
            try:
                tags = discover(product)
                if not db.execute('SELECT 1 FROM initialized WHERE product=?', (product,)).fetchone():
                    db.executemany('INSERT OR IGNORE INTO jobs VALUES (?,?,?,?,NULL)', [(product, tag, sha, 'baseline') for tag, sha in tags.items()])
                    db.execute('INSERT INTO initialized VALUES (?)', (product,))
                    db.commit()
                    print(product, 'baseline recorded; waiting for NEW ready tags', flush=True)
                    continue
                config = json.loads(Path('/config/channels.json').read_text())
                for tag, sha in sorted(tags.items()):
                    if not db.execute('SELECT 1 FROM jobs WHERE product=? AND tag=?', (product, tag)).fetchone():
                        process(db, root, product, tag, sha, config)
            except Exception as exc:
                print(product, 'poll failed:', type(exc).__name__, flush=True)
        write_public(db, public)
        write_android_releases(db, Path(os.environ.get('PRIVATE_RELEASE_DIR', '/releases')))
        time.sleep(60)


if __name__ == '__main__':
    main()

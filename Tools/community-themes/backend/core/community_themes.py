"""Moderated, declarative community themes. No HTTP clients, arbitrary files or bot messages."""
import hashlib
import hmac
import json
import re
import secrets
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path
from urllib.parse import parse_qsl

from flask import Blueprint, abort, jsonify, request, send_from_directory

PREFIX = '/api/public/community-themes'
MAX_FILE = 65536
MAX_BODY = 131072
COLOR_KEYS = frozenset(json.loads(Path(__file__).with_name('community_theme_keys.json').read_text()))


def strict_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate_key')
        result[key] = value
    return result


def color(value):
    if isinstance(value, str) and re.fullmatch(r'#[0-9a-fA-F]{8}', value):
        number = int(value[1:], 16)
    elif isinstance(value, str) and re.fullmatch(r'-?[0-9]{1,10}', value):
        number = int(value)
        if not -2147483648 <= number <= 4294967295:
            raise ValueError('invalid_color')
        number &= 0xffffffff
    else:
        raise ValueError('invalid_color')
    if number >> 24 != 255:
        raise ValueError('opaque_colors_required')
    return '#%08X' % number


def palette(data):
    if 'attheme_text' in data:
        source = data['attheme_text']
        if not isinstance(source, str) or len(source.encode('utf-8')) > MAX_FILE:
            raise ValueError('file_too_large')
        values = {}
        for line in source.splitlines():
            if not line.strip():
                continue
            key, sep, value = line.partition('=')
            if not sep or key in values:
                raise ValueError('invalid_attheme')
            values[key] = value
    else:
        values = data.get('colors')
    if not isinstance(values, dict) or not 1 <= len(values) <= len(COLOR_KEYS):
        raise ValueError('invalid_palette')
    if set(values) - COLOR_KEYS:
        raise ValueError('unsupported_keys')
    colors = {key: color(value) for key, value in sorted(values.items())}
    canonical = ''.join('%s=%d\n' % (key, int(value[1:], 16) - 4294967296) for key, value in colors.items()).encode('utf-8')
    if len(canonical) > MAX_FILE:
        raise ValueError('file_too_large')
    return colors, canonical


def verify_init_data(raw, token, now):
    """Only the injected hub bot token, never every managed child bot token."""
    try:
        if not isinstance(raw, str) or not 1 <= len(raw) <= 16384 or not token:
            return None
        fields = strict_object(parse_qsl(raw, keep_blank_values=True, strict_parsing=True, max_num_fields=32))
        received = fields.pop('hash', '')
        if not re.fullmatch(r'[0-9a-f]{64}', received):
            return None
        at = int(fields.get('auth_date', '0'))
        if at <= 0 or now - at > 300 or at - now > 30:
            return None
        secret = hmac.new(b'WebAppData', token.encode(), hashlib.sha256).digest()
        check = '\n'.join(key + '=' + fields[key] for key in sorted(fields))
        expected = hmac.new(secret, check.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expected, received):
            return None
        user = json.loads(fields.get('user', '{}'), object_pairs_hook=strict_object)
        uid = user.get('id')
        return str(uid) if type(uid) is int and 0 < uid < 2**63 else None
    except (ValueError, TypeError, AttributeError):
        return None


def setup(db_path, bot_token, owner_id, clock=time.time, web_dir=None, public_origin="https://cintiabot.todosobreall.tech"):
    """Callbacks resolve hub token and configured owner at request time; do not log either."""
    bp = Blueprint('community_themes', __name__)
    path = Path(db_path)
    path.parent.mkdir(parents=True, exist_ok=True)

    @contextmanager
    def database():
        db = sqlite3.connect(str(path), timeout=10)
        db.row_factory = sqlite3.Row
        try:
            db.execute('BEGIN IMMEDIATE')
            yield db
            db.commit()
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    with database() as db:
        db.executescript('''
        CREATE TABLE IF NOT EXISTS themes(id TEXT PRIMARY KEY, uid TEXT NOT NULL, title TEXT NOT NULL,
         alias TEXT NOT NULL, alias_public INTEGER NOT NULL, type TEXT NOT NULL, payload TEXT NOT NULL,
         sha256 TEXT NOT NULL, bytes INTEGER NOT NULL, created REAL NOT NULL, status TEXT NOT NULL,
         moderated REAL, moderator TEXT, reason TEXT NOT NULL DEFAULT '');
        CREATE INDEX IF NOT EXISTS themes_owner ON themes(uid,created);
        CREATE INDEX IF NOT EXISTS themes_status ON themes(status,id);
        ''')

    @bp.before_request
    def limits():
        if request.method == 'POST':
            if request.content_length is None or request.content_length > MAX_BODY:
                abort(413)
            if request.mimetype != 'application/json':
                abort(415)
            # Authentication is an explicit Telegram proof, not a ambient browser cookie.
            origin = request.headers.get('Origin')
            if origin and origin.rstrip('/') != public_origin:
                abort(403)

    @bp.after_request
    def headers(response):
        response.headers['X-Content-Type-Options'] = 'nosniff'
        response.headers['Cache-Control'] = 'no-store'
        if request.method == 'GET':
            response.headers['Access-Control-Allow-Origin'] = '*'
        return response

    @bp.errorhandler(ValueError)
    def invalid(error):
        safe = str(error)
        known = {'duplicate_key', 'invalid_color', 'opaque_colors_required', 'file_too_large', 'invalid_attheme',
                 'invalid_palette', 'unsupported_keys', 'invalid_submission', 'invalid_title', 'invalid_alias',
                 'invalid_slug', 'license_required', 'unknown_fields', 'invalid_action', 'invalid_cursor'}
        return jsonify(ok=False, error=safe if safe in known else 'invalid_document'), 400

    def body(allowed):
        data = json.loads(request.get_data(cache=False), object_pairs_hook=strict_object)
        if not isinstance(data, dict):
            raise ValueError('invalid_document')
        if set(data) - allowed:
            raise ValueError('unknown_fields')
        return data

    def identity(data):
        uid = verify_init_data(data.get('initData'), bot_token(), clock())
        if not uid:
            abort(401)
        return uid

    def public(row, private=False):
        result = dict(id=row['id'], revision=1, title=row['title'], author_alias=row['alias'] if row['alias_public'] else '', type=row['type'])
        payload = json.loads(row['payload'])
        if row['type'] == 'attheme':
            result.update(format='telebots-theme-v1', colors=payload, sha256=row['sha256'], bytes=row['bytes'])
        else:
            result.update(telegram_slug=payload, telegram_url='https://t.me/addtheme/' + payload)
        if private:
            result.update(status=row['status'], reason=row['reason'], created_at=row['created'])
        return result

    @bp.get(PREFIX)
    def catalog():
        cursor = request.args.get('cursor', '')
        if cursor and not re.fullmatch(r'[a-f0-9]{32}', cursor):
            raise ValueError('invalid_cursor')
        try:
            count = int(request.args.get('limit', '20'))
        except ValueError:
            raise ValueError('invalid_cursor')
        count = max(1, min(50, count))
        with database() as db:
            rows = db.execute("SELECT * FROM themes WHERE status='approved' AND id>? ORDER BY id LIMIT ?", (cursor, count + 1)).fetchall()
        return jsonify(ok=True, themes=[public(row) for row in rows[:count]], next_cursor=rows[count - 1]['id'] if len(rows) > count else None)

    @bp.get(PREFIX + '/<theme_id>')
    def detail(theme_id):
        if not re.fullmatch(r'[a-f0-9]{32}', theme_id):
            abort(404)
        with database() as db:
            row = db.execute("SELECT * FROM themes WHERE id=? AND status='approved'", (theme_id,)).fetchone()
        if not row:
            abort(404)
        return jsonify(ok=True, theme=public(row))

    @bp.post(PREFIX + '/submit')
    def submit():
        data = body({'initData', 'title', 'alias', 'alias_public', 'license_ack', 'telegram_slug', 'attheme_text', 'colors'})
        uid = identity(data)
        if data.get('license_ack') is not True:
            raise ValueError('license_required')
        title, alias = data.get('title'), data.get('alias', '')
        if not isinstance(title, str) or not 1 <= len(title.strip()) <= 80 or any(ord(c) < 32 for c in title):
            raise ValueError('invalid_title')
        if not isinstance(alias, str) or len(alias.strip()) > 40 or any(ord(c) < 32 for c in alias) or type(data.get('alias_public', False)) is not bool:
            raise ValueError('invalid_alias')
        if sum(key in data for key in ('telegram_slug', 'colors', 'attheme_text')) != 1:
            raise ValueError('invalid_submission')
        if 'telegram_slug' in data:
            payload = data['telegram_slug']
            if not isinstance(payload, str) or not re.fullmatch(r'[A-Za-z0-9_-]{1,128}', payload):
                raise ValueError('invalid_slug')
            kind, sha, size = 'telegram_link', '', 0
        else:
            payload, canonical = palette(data)
            kind, sha, size = 'attheme', hashlib.sha256(canonical).hexdigest(), len(canonical)
        with database() as db:
            # Bounds storage independently of per-user quotas. Never silently remove approved work.
            db.execute("DELETE FROM themes WHERE status IN ('pending','rejected') AND created<?", (clock() - 30 * 86400,))
            pending = db.execute("SELECT COUNT(*) FROM themes WHERE uid=? AND status='pending'", (uid,)).fetchone()[0]
            daily = db.execute('SELECT COUNT(*) FROM themes WHERE uid=? AND created>?', (uid, clock() - 86400)).fetchone()[0]
            total, stored = db.execute('SELECT COUNT(*),COALESCE(SUM(LENGTH(payload)),0) FROM themes').fetchone()
            if pending >= 3 or daily >= 5:
                return jsonify(ok=False, error='quota_exceeded'), 429
            if total >= 10000 or stored + len(json.dumps(payload)) > 32 * 1024 * 1024:
                return jsonify(ok=False, error='catalog_capacity'), 503
            theme_id = secrets.token_hex(16)
            db.execute('INSERT INTO themes(id,uid,title,alias,alias_public,type,payload,sha256,bytes,created,status) VALUES(?,?,?,?,?,?,?,?,?,?,?)',
                       (theme_id, uid, title.strip(), alias.strip(), int(data.get('alias_public', False)), kind,
                        json.dumps(payload, sort_keys=True, separators=(',', ':')), sha, size, clock(), 'pending'))
        return jsonify(ok=True, id=theme_id, status='pending'), 201

    @bp.post(PREFIX + '/mine')
    def mine():
        data = body({'initData'})
        uid = identity(data)
        owner = uid == str(owner_id())
        with database() as db:
            rows = db.execute("SELECT * FROM themes WHERE uid=? OR (? AND status='pending') ORDER BY created DESC LIMIT 100", (uid, int(owner))).fetchall()
        return jsonify(ok=True, themes=[public(row, True) for row in rows], is_owner=owner)

    @bp.post(PREFIX + '/moderate')
    def moderate():
        data = body({'initData', 'id', 'action', 'reason'})
        uid = identity(data)
        if uid != str(owner_id()):
            abort(403)
        action, reason = data.get('action'), data.get('reason', '')
        if action not in ('approve', 'reject') or not isinstance(reason, str) or len(reason) > 300:
            raise ValueError('invalid_action')
        if not isinstance(data.get('id'), str) or not re.fullmatch(r'[a-f0-9]{32}', data['id']):
            abort(404)
        with database() as db:
            changed = db.execute('UPDATE themes SET status=?,moderated=?,moderator=?,reason=? WHERE id=?',
                                 ('approved' if action == 'approve' else 'rejected', clock(), uid, reason, data['id'])).rowcount
            if not changed:
                abort(404)
        return jsonify(ok=True, status='approved' if action == 'approve' else 'rejected')

    if web_dir is not None:
        @bp.get('/community-theme')
        def bridge():
            return send_from_directory(str(web_dir), 'community-theme.html')

    return bp

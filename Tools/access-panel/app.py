"""Private branch access. Identity comes only from the authenticated Moonbot bridge.

The client gate is distribution control, not DRM: modified clients can remove it.
Never accept a client-supplied Telegram ID as authentication.
"""
import hashlib
import hmac
import json
import os
import re
import secrets
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path
from urllib.parse import urlsplit

from flask import Flask, abort, jsonify, request, send_from_directory

CHANNELS = ('dev', 'alfa', 'beta', 'rc', 'stable')
PRODUCTS = ('android', 'react')
COOKIE = '__Host-telebots_access'
PREFIX = '/access/api'
COUNTRY_CODES = frozenset('AD AE AF AG AI AL AM AO AQ AR AS AT AU AW AX AZ BA BB BD BE BF BG BH BI BJ BL BM BN BO BQ BR BS BT BV BW BY BZ CA CC CD CF CG CH CI CK CL CM CN CO CR CU CV CW CX CY CZ DE DJ DK DM DO DZ EC EE EG EH ER ES ET FI FJ FK FM FO FR GA GB GD GE GF GG GH GI GL GM GN GP GQ GR GS GT GU GW GY HK HM HN HR HT HU ID IE IL IM IN IO IQ IR IS IT JE JM JO JP KE KG KH KI KM KN KP KR KW KY KZ LA LB LC LI LK LR LS LT LU LV LY MA MC MD ME MF MG MH MK ML MM MN MO MP MQ MR MS MT MU MV MW MX MY MZ NA NC NE NF NG NI NL NO NP NR NU NZ OM PA PE PF PG PH PK PL PM PN PR PS PT PW PY QA RE RO RS RU RW SA SB SC SD SE SG SH SI SJ SK SL SM SN SO SR SS ST SV SX SY SZ TC TD TF TG TH TJ TK TL TM TN TO TR TT TV TW TZ UA UG UM US UY UZ VA VC VE VG VI VN VU WF WS YE YT ZA ZM ZW'.split())


def digest(value):
    return hashlib.sha256(value.encode()).hexdigest()


def create_app(config=None):
    app = Flask(__name__, static_folder=None)
    app.config.update(DB_PATH=os.environ.get('ACCESS_DB', '/data/access.sqlite'),
                      PUBLIC_URL=os.environ.get('ACCESS_PUBLIC_URL', 'https://chat.todosobreall.tech'),
                      BOT_USERNAME=os.environ.get('ACCESS_BOT_USERNAME', 'CintiaBot'),
                      OWNER_ID=os.environ.get('ACCESS_OWNER_ID', ''),
                      BRIDGE_SECRET=os.environ.get('ACCESS_BRIDGE_SECRET', ''),
                      TRUSTED_ORIGINS=os.environ.get('ACCESS_TRUSTED_ORIGINS', ''),
                      RELEASE_DIR=os.environ.get('ACCESS_RELEASE_DIR', '/releases'),
                      MAX_CONTENT_LENGTH=8192, CLOCK=time.time)
    if config:
        app.config.update(config)
    if not re.fullmatch(r'[1-9][0-9]{0,18}', str(app.config['OWNER_ID'])):
        raise ValueError('ACCESS_OWNER_ID must be configured explicitly')
    if len(app.config['BRIDGE_SECRET']) < 32:
        raise ValueError('ACCESS_BRIDGE_SECRET must contain at least 32 random characters')
    origin = app.config['PUBLIC_URL'].rstrip('/')
    parsed = urlsplit(origin)
    if parsed.scheme != 'https' or not parsed.netloc or parsed.username or parsed.password or parsed.path or parsed.query or parsed.fragment:
        raise ValueError('ACCESS_PUBLIC_URL must be an HTTPS origin')
    if not re.fullmatch(r'[A-Za-z0-9_]{5,32}', app.config['BOT_USERNAME']):
        raise ValueError('Invalid bot username')
    origins = {origin}
    for extra in app.config['TRUSTED_ORIGINS'].split(','):
        if not extra.strip():
            continue
        extra = extra.strip().rstrip('/')
        url = urlsplit(extra)
        if url.scheme != 'https' or not url.netloc or url.username or url.password or url.path or url.query or url.fragment:
            raise ValueError('ACCESS_TRUSTED_ORIGINS must contain HTTPS origins')
        origins.add(extra)
    Path(app.config['DB_PATH']).parent.mkdir(parents=True, exist_ok=True)

    @contextmanager
    def database():
        db = sqlite3.connect(app.config['DB_PATH'], timeout=15)
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
        CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY, first_name TEXT, username TEXT);
        CREATE TABLE IF NOT EXISTS grants(uid TEXT, product TEXT, channel TEXT, PRIMARY KEY(uid,product,channel));
        CREATE TABLE IF NOT EXISTS challenges(hash TEXT PRIMARY KEY, code TEXT UNIQUE, kind TEXT,
          product TEXT, channel TEXT, version TEXT, created REAL, expires REAL, uid TEXT, state TEXT, source TEXT);
        CREATE TABLE IF NOT EXISTS sessions(id TEXT PRIMARY KEY, hash TEXT UNIQUE, uid TEXT, kind TEXT,
          product TEXT, channel TEXT, version TEXT, created REAL, expires REAL, last_seen REAL,
          revoked INTEGER DEFAULT 0, csrf TEXT);
        CREATE TABLE IF NOT EXISTS audit(id INTEGER PRIMARY KEY, at REAL, actor TEXT, action TEXT, target TEXT);
        CREATE TABLE IF NOT EXISTS requests(uid TEXT, product TEXT, channel TEXT, at REAL, PRIMARY KEY(uid,product,channel));
        CREATE TABLE IF NOT EXISTS country_preferences(uid TEXT PRIMARY KEY, country TEXT NOT NULL, consented_at REAL NOT NULL, consent_version TEXT NOT NULL);
        CREATE INDEX IF NOT EXISTS session_uid ON sessions(uid);
        CREATE INDEX IF NOT EXISTS challenge_source ON challenges(source,created);
        ''')

    def now():
        return app.config['CLOCK']()

    def body():
        value = request.get_json(silent=True)
        if not isinstance(value, dict):
            abort(400)
        return value

    def bearer():
        auth = request.headers.get('Authorization', '')
        return auth[7:] if auth.startswith('Bearer ') and len(auth) < 200 else ''

    def allowed(db, uid, product, channel):
        return channel == 'stable' or uid == str(app.config['OWNER_ID']) or db.execute(
            'SELECT 1 FROM grants WHERE uid=? AND product=? AND channel=?', (uid, product, channel)).fetchone() is not None

    def user_data(db, session):
        user = dict(db.execute('SELECT * FROM users WHERE id=?', (session['uid'],)).fetchone())
        return dict(user=user, is_admin=session['uid'] == str(app.config['OWNER_ID']), csrf_token=session['csrf'])

    def web_session(db, admin=False, csrf=False):
        row = db.execute("SELECT * FROM sessions WHERE hash=? AND kind='web'", (digest(request.cookies.get(COOKIE, '')),)).fetchone()
        if not row or row['revoked'] or row['expires'] <= now():
            abort(401)
        if admin and row['uid'] != str(app.config['OWNER_ID']):
            abort(403)
        if csrf and not hmac.compare_digest(request.headers.get('X-CSRF-Token', ''), row['csrf']):
            abort(403)
        return row

    def new_session(db, challenge):
        token, sid = secrets.token_urlsafe(32), secrets.token_hex(16)
        ttl = 3600 if challenge['kind'] == 'web' else 300
        db.execute('INSERT INTO sessions VALUES(?,?,?,?,?,?,?,?,?,?,0,?)',
                   (sid, digest(token), challenge['uid'], challenge['kind'], challenge['product'],
                    challenge['channel'], challenge['version'], now(), now() + ttl, now(), secrets.token_urlsafe(24)))
        return token, db.execute('SELECT * FROM sessions WHERE id=?', (sid,)).fetchone()

    @app.before_request
    def same_origin():
        # Bearer and bridge requests are not cookie-authenticated; web mutations also require CSRF.
        supplied = request.headers.get('Origin')
        if supplied and supplied not in origins:
            abort(403)

    @app.after_request
    def secure(response):
        response.headers['Cache-Control'] = 'no-store'
        response.headers['X-Content-Type-Options'] = 'nosniff'
        response.headers['Referrer-Policy'] = 'no-referrer'
        response.headers['Content-Security-Policy'] = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"
        return response

    @app.errorhandler(400)
    @app.errorhandler(401)
    @app.errorhandler(403)
    @app.errorhandler(404)
    @app.errorhandler(409)
    @app.errorhandler(413)
    @app.errorhandler(429)
    def error(exc):
        return jsonify(error={400: 'invalid_request', 401: 'authentication_required', 403: 'access_denied',
                              404: 'not_found', 409: 'already_used', 413: 'request_too_large', 429: 'rate_limited'}[exc.code]), exc.code

    @app.get(PREFIX + '/config')
    def configuration():
        return jsonify(bot_username=app.config['BOT_USERNAME'], auth_method='moonbot', products=PRODUCTS, channels=CHANNELS)

    def start(kind):
        data = body() if kind == 'device' else {}
        product, channel = data.get('product', ''), data.get('channel', '')
        if kind == 'device' and (product not in PRODUCTS or channel not in CHANNELS):
            abort(400)
        if product == 'android' and data.get('package') != 'org.comunidadtelebots.' + channel:
            abort(400)
        version = data.get('version', '')
        if not isinstance(version, str) or len(version) > 80:
            abort(400)
        token, code = secrets.token_urlsafe(32), secrets.token_hex(12)
        # Never trust client-supplied forwarded IPs. Traefik's direct peer gets a generous shared cap.
        source = digest(request.remote_addr or 'unknown')
        with database() as db:
            db.execute('DELETE FROM challenges WHERE expires<?', (now() - 300,))
            db.execute('DELETE FROM sessions WHERE expires<?', (now() - 30 * 86400,))
            db.execute('DELETE FROM audit WHERE at<?', (now() - 90 * 86400,))
            if db.execute('SELECT COUNT(*) FROM challenges WHERE source=? AND created>?', (source, now() - 60)).fetchone()[0] >= 100:
                abort(429)
            if db.execute('SELECT COUNT(*) FROM challenges').fetchone()[0] >= 10000:
                abort(429)
            db.execute('INSERT INTO challenges VALUES(?,?,?,?,?,?,?,?,?,?,?)',
                       (digest(token), code, kind, product, channel, version, now(), now() + 300, None, 'pending', source))
        link = 'https://t.me/' + app.config['BOT_USERNAME'] + '?start=access_' + code
        if kind == 'web':
            response = jsonify(login_token=token, user_code=code, verification_uri=link, expires_in=300, interval=5)
            # Bind approval to the browser that initiated login, preventing login-CSRF token injection.
            response.set_cookie('__Host-access_pair', digest(token), secure=True, httponly=True, samesite='Strict', max_age=300, path='/')
            return response
        return jsonify(device_token=token, user_code=code, verification_uri=origin + '/access/?code=' + code, expires_in=300, interval=5)

    @app.post(PREFIX + '/device/start')
    def device_start():
        return start('device')

    @app.get(PREFIX + '/auth/start')
    def auth_start():
        return start('web')

    def poll(kind):
        token = body().get('login_token', '') if kind == 'web' else bearer()
        if not isinstance(token, str) or len(token) > 100:
            abort(400)
        hashed = digest(token)
        if kind == 'web' and not hmac.compare_digest(request.cookies.get('__Host-access_pair', ''), hashed):
            abort(403)
        with database() as db:
            row = db.execute('SELECT * FROM challenges WHERE hash=? AND kind=?', (hashed, kind)).fetchone()
            if not row or row['expires'] <= now() or row['state'] in ('used', 'revoked'):
                return jsonify(status='revoked'), 403
            if row['state'] == 'pending':
                return jsonify(status='pending')
            if kind == 'device' and not allowed(db, row['uid'], row['product'], row['channel']):
                return jsonify(status='revoked'), 403
            session_token, session = new_session(db, row)
            db.execute("UPDATE challenges SET state='used' WHERE hash=?", (hashed,))
            if kind == 'web':
                response = jsonify(status='approved', **user_data(db, session))
                response.set_cookie(COOKIE, session_token, secure=True, httponly=True, samesite='Strict', max_age=3600, path='/')
                response.delete_cookie('__Host-access_pair', secure=True, httponly=True, samesite='Strict', path='/')
                return response
            return jsonify(status='approved', session_token=session_token, expires_in=300)

    @app.post(PREFIX + '/device/poll')
    def device_poll():
        return poll('device')

    @app.post(PREFIX + '/auth/poll')
    def auth_poll():
        return poll('web')

    @app.get(PREFIX + '/auth/session')
    def auth_session():
        with database() as db:
            return jsonify(**user_data(db, web_session(db)))

    @app.post(PREFIX + '/auth/logout')
    def logout():
        with database() as db:
            row = web_session(db, csrf=True)
            db.execute('UPDATE sessions SET revoked=1 WHERE id=?', (row['id'],))
        response = jsonify(ok=True)
        response.delete_cookie(COOKIE, secure=True, httponly=True, samesite='Strict', path='/')
        return response

    def approve(db, code, uid):
        row = db.execute('SELECT * FROM challenges WHERE code=?', (code,)).fetchone()
        if not row or row['expires'] <= now() or row['state'] != 'pending':
            abort(409)
        if row['kind'] == 'device' and not allowed(db, uid, row['product'], row['channel']):
            abort(403)
        db.execute("UPDATE challenges SET uid=?,state='approved' WHERE code=?", (uid, code))
        return row

    @app.post(PREFIX + '/device/approve')
    def device_approve():
        data = body()
        with database() as db:
            row = web_session(db, csrf=True)
            challenge = db.execute('SELECT kind FROM challenges WHERE code=?', (data.get('code', ''),)).fetchone()
            if not challenge or challenge['kind'] != 'device':
                abort(400)
            approve(db, data.get('code', ''), row['uid'])
        return jsonify(ok=True)

    @app.get(PREFIX + '/device/info')
    def device_info():
        code = request.args.get('code', '')
        if not re.fullmatch(r'[a-f0-9]{24}', code):
            abort(400)
        with database() as db:
            row = db.execute("SELECT product,channel,expires FROM challenges WHERE code=? AND kind='device' AND state='pending'", (code,)).fetchone()
            if not row or row['expires'] <= now():
                abort(404)
            return jsonify(product=row['product'], channel=row['channel'], expires_in=int(row['expires'] - now()))

    @app.post(PREFIX + '/bridge/inspect')
    @app.post(PREFIX + '/bridge/approve')
    def bridge():
        if not hmac.compare_digest(bearer(), app.config['BRIDGE_SECRET']):
            abort(401)
        data = body()
        if not isinstance(data.get('code'), str) or not re.fullmatch(r'[a-f0-9]{24}', data['code']):
            abort(400)
        with database() as db:
            row = db.execute('SELECT * FROM challenges WHERE code=?', (data.get('code', ''),)).fetchone()
            if not row or row['expires'] <= now() or row['state'] != 'pending':
                abort(409)
            if request.path.endswith('/inspect'):
                # No Telegram identity is needed to describe a one-time request.
                return jsonify(ok=True, kind=row['kind'], product=row['product'], channel=row['channel'])
            user = data.get('user', {})
            if data.get('consent') is not True or not isinstance(user, dict) or not re.fullmatch(r'[1-9][0-9]{0,18}', str(user.get('id', ''))):
                abort(400)
            uid = str(user['id'])
            first_name, username = user.get('first_name', ''), user.get('username', '')
            if not isinstance(first_name, str) or not isinstance(username, str) or max(len(first_name), len(username)) > 128:
                abort(400)
            db.execute('INSERT INTO users VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET first_name=excluded.first_name,username=excluded.username', (uid, first_name, username))
            db.execute('INSERT INTO audit(at,actor,action,target) VALUES(?,?,?,?)', (now(), uid, 'consent_access:2026-10-07', uid))
            if row['kind'] == 'device':
                db.execute('INSERT INTO requests VALUES(?,?,?,?) ON CONFLICT(uid,product,channel) DO UPDATE SET at=excluded.at', (uid, row['product'], row['channel'], now()))
                if not allowed(db, uid, row['product'], row['channel']):
                    # Consent registers the request, never grants unauthorized access.
                    return jsonify(error='permission_pending', registered=True), 403
            approve(db, row['code'], uid)
            db.execute('INSERT INTO audit(at,actor,action,target) VALUES(?,?,?,?)', (now(), uid, 'approve_' + row['kind'], row['product'] + ':' + row['channel']))
            return jsonify(ok=True, kind=row['kind'], product=row['product'], channel=row['channel'],
                           allowed=row['kind'] == 'web' or allowed(db, uid, row['product'], row['channel']))

    @app.post(PREFIX + '/session/check')
    def check():
        with database() as db:
            row = db.execute("SELECT * FROM sessions WHERE hash=? AND kind='device'", (digest(bearer()),)).fetchone()
            if not row or row['revoked'] or row['expires'] <= now() or not allowed(db, row['uid'], row['product'], row['channel']):
                return jsonify(allowed=False), 403
            # Absolute daily reauthentication bounds stolen bearer lifetime even with heartbeats.
            expires = min(now() + 300, row['created'] + 86400)
            if expires <= now():
                return jsonify(allowed=False), 403
            db.execute('UPDATE sessions SET expires=?,last_seen=? WHERE id=?', (expires, now(), row['id']))
            return jsonify(allowed=True, expires_in=int(expires - now()))

    @app.post(PREFIX + '/android/update')
    def android_update():
        data = body()
        channel, package, code = data.get('channel'), data.get('package'), data.get('version_code')
        if (channel not in CHANNELS or package != 'org.comunidadtelebots.' + channel or
                type(code) is not int or not 0 < code <= 2100000000):
            abort(400)
        if channel != 'stable':
            with database() as db:
                row = db.execute("SELECT * FROM sessions WHERE hash=? AND kind='device' AND product='android' AND channel=?", (digest(bearer()), channel)).fetchone()
                if (not row or row['revoked'] or row['expires'] <= now() or row['created'] + 86400 <= now() or
                        not allowed(db, row['uid'], 'android', channel)):
                    abort(403)
        path = Path(app.config['RELEASE_DIR']) / 'android' / (channel + '.json')
        try:
            if path.stat().st_size > 16384:
                raise ValueError('oversized')
            record = json.loads(path.read_text(encoding='utf-8'))
            if (record.get('confirmed') is not True or record.get('product') != 'android' or
                    record.get('channel') != channel or record.get('package') != package or
                    type(record.get('version_code')) is not int or not 0 < record['version_code'] <= 2100000000 or
                    not isinstance(record.get('version_name'), str) or not 0 < len(record['version_name']) <= 100 or
                    not isinstance(record.get('url'), str) or not re.fullmatch(r'https://t\.me/(?:c/[1-9][0-9]*|[A-Za-z][A-Za-z0-9_]{4,31})/[1-9][0-9]*', record['url'])):
                raise ValueError('invalid release')
        except (OSError, ValueError, TypeError, AttributeError):
            return jsonify(status='unavailable')
        available = record['version_code'] > code
        result = dict(status='available' if available else 'current', version_name=record['version_name'], version_code=record['version_code'])
        result.update(package=package, channel=channel)
        if type(record.get('size_bytes')) is int and 0 < record['size_bytes'] <= 2147483647:
            result['size_bytes'] = record['size_bytes']
        if type(record.get('min_sdk')) is int and 1 <= record['min_sdk'] <= 1000:
            result['min_sdk'] = record['min_sdk']
        if isinstance(record.get('sha256'), str) and re.fullmatch(r'[a-f0-9]{64}', record['sha256']):
            result['sha256'] = record['sha256']
        if isinstance(record.get('signer_sha256'), list) and 1 <= len(record['signer_sha256']) <= 8 and all(isinstance(cert, str) and re.fullmatch(r'[a-f0-9]{64}', cert) for cert in record['signer_sha256']):
            result['signer_sha256'] = record['signer_sha256']
        if isinstance(record.get('abis'), list) and len(record['abis']) <= 16 and all(isinstance(abi, str) and re.fullmatch(r'[a-zA-Z0-9_-]{1,32}', abi) for abi in record['abis']):
            result['abis'] = record['abis']
        if isinstance(record.get('release_notes'), str) and len(record['release_notes']) <= 2000 and len(record['release_notes'].encode()) <= 4000:
            result['release_notes'] = record['release_notes']
        if available:
            result['url'] = record['url']
        return jsonify(**result)

    @app.get(PREFIX + '/react/authorize')
    def react_authorize():
        channel = request.args.get('channel', '')
        if channel not in CHANNELS:
            abort(400)
        if channel == 'stable':
            return jsonify(allowed=True, public=True)
        with database() as db:
            row = web_session(db)
            if not allowed(db, row['uid'], 'react', channel):
                abort(403)
            # Keep a separate presence record from the administration login.
            sid = digest(row['id'] + ':react:' + channel)
            seen = db.execute('SELECT revoked FROM sessions WHERE id=?', (sid,)).fetchone()
            if seen and seen['revoked']:
                abort(403)
            db.execute("INSERT INTO sessions(id,hash,uid,kind,product,channel,version,created,expires,last_seen,csrf) VALUES(?,?,?,'react','react',?,'',?,?,?,'') ON CONFLICT(id) DO UPDATE SET last_seen=excluded.last_seen,expires=excluded.expires",
                       (sid, digest(sid), row['uid'], channel, now(), min(now() + 300, row['expires']), now()))
        return jsonify(allowed=True, expires_in=300)

    @app.get(PREFIX + '/admin/users')
    def users():
        product, channel = request.args.get('product', ''), request.args.get('channel', '')
        if (product and product not in PRODUCTS) or (channel and channel not in CHANNELS):
            abort(400)
        with database() as db:
            web_session(db, admin=True)
            result = []
            for row in db.execute('SELECT * FROM users ORDER BY first_name,id'):
                uid = row['id']
                item = dict(row)
                item['grants'] = {p: [r[0] for r in db.execute('SELECT channel FROM grants WHERE uid=? AND product=?', (uid, p))] for p in PRODUCTS}
                item['requests'] = [dict(r) for r in db.execute('SELECT product,channel,at FROM requests WHERE uid=?', (uid,))]
                item['sessions'] = [dict(r) for r in db.execute("SELECT id,product,channel,version,last_seen,expires,revoked FROM sessions WHERE uid=? AND kind IN ('device','react') ORDER BY last_seen DESC LIMIT 100", (uid,))]
                for session in item['sessions']:
                    session['active'] = not session['revoked'] and session['expires'] > now() and session['last_seen'] > now() - 180
                item['last_seen'] = max((s['last_seen'] for s in item['sessions']), default=None)
                if channel:
                    products = [product] if product else PRODUCTS
                    membership = any(channel in item['grants'][p] for p in products)
                    membership |= any(s['channel'] == channel and s['product'] in products for s in item['sessions'] + item['requests'])
                    if not membership and uid != str(app.config['OWNER_ID']):
                        continue
                result.append(item)
            return jsonify(users=result, active_window=180, stable_public=True, owner_id=str(app.config['OWNER_ID']))

    @app.get(PREFIX + '/profile')
    def profile():
        with database() as db:
            row = web_session(db)
            preference = db.execute('SELECT country FROM country_preferences WHERE uid=?', (row['uid'],)).fetchone()
            return jsonify(country=preference['country'] if preference else None, countries=sorted(COUNTRY_CODES))

    @app.post(PREFIX + '/profile/country')
    def profile_country():
        data = body()
        country = data.get('country')
        if country is not None and (not isinstance(country, str) or country not in COUNTRY_CODES or data.get('consent') is not True):
            abort(400)
        with database() as db:
            row = web_session(db, csrf=True)
            if country is None:
                db.execute('DELETE FROM country_preferences WHERE uid=?', (row['uid'],))
            else:
                db.execute('INSERT INTO country_preferences VALUES(?,?,?,?) ON CONFLICT(uid) DO UPDATE SET country=excluded.country,consented_at=excluded.consented_at,consent_version=excluded.consent_version', (row['uid'], country, now(), 'country-2026-10-07'))
        return jsonify(ok=True, country=country)

    @app.get(PREFIX + '/admin/countries')
    def countries():
        with database() as db:
            web_session(db, admin=True)
            counts = db.execute('SELECT country,COUNT(*) AS count FROM country_preferences GROUP BY country').fetchall()
            return jsonify(countries=[dict(r) for r in counts if r['count'] >= 3], minimum_group=3,
                           suppressed=sum(r['count'] for r in counts if r['count'] < 3), basis='voluntary_self_report')

    @app.get(PREFIX + '/profile/export')
    def export_profile():
        with database() as db:
            row = web_session(db)
            uid = row['uid']
            result = {'profile': dict(db.execute('SELECT * FROM users WHERE id=?', (uid,)).fetchone()),
                      'grants': [dict(r) for r in db.execute('SELECT product,channel FROM grants WHERE uid=?', (uid,))],
                      'requests': [dict(r) for r in db.execute('SELECT product,channel,at FROM requests WHERE uid=?', (uid,))],
                      'sessions': [dict(r) for r in db.execute('SELECT kind,product,channel,version,created,expires,last_seen,revoked FROM sessions WHERE uid=?', (uid,))],
                      'country': [dict(r) for r in db.execute('SELECT country,consented_at,consent_version FROM country_preferences WHERE uid=?', (uid,))],
                      'audit': [{'at': r['at'], 'action': r['action']} for r in db.execute('SELECT at,action FROM audit WHERE actor=? OR target=?', (uid, uid))]}
        response = jsonify(**result)
        response.headers['Content-Disposition'] = 'attachment; filename="alltech-access-profile.json"'
        return response

    @app.post(PREFIX + '/profile/delete')
    def delete_profile():
        if body().get('confirm') != 'delete_access_profile':
            abort(400)
        with database() as db:
            row = web_session(db, csrf=True)
            uid = row['uid']
            # A prior device-revocation audit target is a session id, not a Telegram id.
            db.execute('DELETE FROM audit WHERE actor=? OR target=? OR target IN (SELECT id FROM sessions WHERE uid=?)', (uid, uid, uid))
            for table in ('grants', 'requests', 'sessions', 'challenges', 'country_preferences'):
                db.execute('DELETE FROM ' + table + ' WHERE uid=?', (uid,))
            db.execute('DELETE FROM users WHERE id=?', (uid,))
        response = jsonify(ok=True, scope='alltech_access_profile_only')
        response.delete_cookie(COOKIE, secure=True, httponly=True, samesite='Strict', path='/')
        response.delete_cookie('__Host-access_pair', secure=True, httponly=True, samesite='Strict', path='/')
        return response

    @app.put(PREFIX + '/admin/grants')
    def grants():
        data = body()
        uid, product, channels = str(data.get('user_id', '')), data.get('product'), data.get('channels')
        if not re.fullmatch(r'[1-9][0-9]{0,18}', uid) or product not in PRODUCTS or not isinstance(channels, list) or any(c not in CHANNELS for c in channels):
            abort(400)
        with database() as db:
            actor = web_session(db, admin=True, csrf=True)
            db.execute('INSERT OR IGNORE INTO users VALUES(?,?,?)', (uid, '', ''))
            previous = {r[0] for r in db.execute('SELECT channel FROM grants WHERE uid=? AND product=?', (uid, product))}
            db.execute('DELETE FROM grants WHERE uid=? AND product=?', (uid, product))
            db.executemany('INSERT INTO grants VALUES(?,?,?)', [(uid, product, c) for c in set(channels)])
            for removed in previous - set(channels):
                db.execute("UPDATE sessions SET revoked=1 WHERE uid=? AND product=? AND channel=? AND kind IN ('device','react')", (uid, product, removed))
                db.execute("UPDATE challenges SET state='revoked' WHERE uid=? AND product=? AND channel=?", (uid, product, removed))
            db.execute('INSERT INTO audit(at,actor,action,target) VALUES(?,?,?,?)', (now(), actor['uid'], 'grants:' + product + ':' + ','.join(sorted(set(channels))), uid))
        return jsonify(ok=True)

    @app.post(PREFIX + '/admin/revoke')
    def revoke():
        data = body()
        with database() as db:
            actor = web_session(db, admin=True, csrf=True)
            sid = data.get('session_id', '')
            changed = db.execute("UPDATE sessions SET revoked=1 WHERE id=? AND kind IN ('device','react')", (sid,)).rowcount
            if not changed:
                abort(404)
            db.execute('INSERT INTO audit(at,actor,action,target) VALUES(?,?,?,?)', (now(), actor['uid'], 'revoke', sid))
        return jsonify(ok=True)

    @app.get('/access/privacy')
    def privacy():
        return send_from_directory(Path(__file__).parent / 'frontend', 'privacy.html')

    @app.get('/access/terms')
    def terms():
        return send_from_directory(Path(__file__).parent / 'frontend', 'terms.html')

    @app.get('/access/')
    @app.get('/access/<path:filename>')
    def frontend(filename='index.html'):
        return send_from_directory(Path(__file__).parent / 'frontend', filename)

    return app

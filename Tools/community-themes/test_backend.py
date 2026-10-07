import hashlib
import hmac
import importlib.util
import json
import pathlib
import tempfile
import unittest
from urllib.parse import urlencode
from flask import Flask

HERE = pathlib.Path(__file__).parent
spec = importlib.util.spec_from_file_location('themes', HERE / 'backend/core/community_themes.py')
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class ThemesTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.now = 1800000000
        self.token = 'unit-test-hub-token-not-a-real-credential'
        app = Flask(__name__)
        app.config['TESTING'] = True
        app.register_blueprint(mod.setup(pathlib.Path(self.tmp.name) / 'themes.sqlite', lambda: self.token,
                                        lambda: 1, lambda: self.now))
        self.client = app.test_client()

    def tearDown(self):
        self.tmp.cleanup()

    def auth(self, uid=2, at=None, token=None):
        fields = {'auth_date': str(self.now if at is None else at), 'user': json.dumps({'id': uid}), 'query_id': 'test-query'}
        secret = hmac.new(b'WebAppData', (token or self.token).encode(), hashlib.sha256).digest()
        check = '\n'.join(k + '=' + fields[k] for k in sorted(fields))
        fields['hash'] = hmac.new(secret, check.encode(), hashlib.sha256).hexdigest()
        return urlencode(fields)

    def post(self, path, data):
        return self.client.post(mod.PREFIX + path, json=data)

    def submission(self, **extra):
        data = dict(initData=self.auth(), title='Blue', license_ack=True, colors={'chat_inBubble': '#FF112233'})
        data.update(extra)
        return data

    def approve(self, theme_id):
        return self.post('/moderate', dict(initData=self.auth(1), id=theme_id, action='approve'))

    def test_pending_private_approved_public_integrity(self):
        response = self.post('/submit', self.submission(alias='Private Alias'))
        self.assertEqual(response.status_code, 201)
        ident = response.json['id']
        self.assertEqual(self.client.get(mod.PREFIX).json['themes'], [])
        self.assertEqual(self.client.get(mod.PREFIX + '/' + ident).status_code, 404)
        self.assertEqual(self.approve(ident).status_code, 200)
        theme = self.client.get(mod.PREFIX + '/' + ident).json['theme']
        self.assertEqual(theme['author_alias'], '')
        self.assertNotIn('uid', theme)
        canonical = b'chat_inBubble=-15654349\n'
        self.assertEqual(theme['sha256'], hashlib.sha256(canonical).hexdigest())
        self.assertEqual(theme['bytes'], len(canonical))
        self.assertEqual(theme['colors']['chat_inBubble'], '#FF112233')
        self.assertNotIn('Private Alias', json.dumps(theme))

    def test_only_hub_fresh_nonduplicate_proof(self):
        invalid = ['', self.auth(token='another-child-bot'), self.auth(at=self.now-301),
                   self.auth(at=self.now+31), self.auth()+'&auth_date='+str(self.now), self.auth(uid=True)]
        for proof in invalid:
            self.assertEqual(self.post('/submit', self.submission(initData=proof)).status_code, 401)
        self.token = ''
        self.assertEqual(self.post('/mine', dict(initData=self.auth(token='unused'))).status_code, 401)

    def test_owner_only_moderation_and_visibility(self):
        ident = self.post('/submit', self.submission()).json['id']
        self.assertEqual(self.post('/moderate', dict(initData=self.auth(), id=ident, action='approve')).status_code, 403)
        self.assertEqual(len(self.post('/mine', dict(initData=self.auth(3))).json['themes']), 0)
        owner = self.post('/mine', dict(initData=self.auth(1))).json
        self.assertTrue(owner['is_owner'])
        self.assertEqual(len(owner['themes']), 1)
        self.approve(ident)
        self.post('/moderate', dict(initData=self.auth(1), id=ident, action='reject', reason='Withdrawn'))
        self.assertEqual(self.client.get(mod.PREFIX + '/' + ident).status_code, 404)

    def test_no_unsafe_or_unsupported_content(self):
        for values in ({'WLS': 'https://private-host/'}, {'wallpaperFileOffset': '#FF112233'},
                       {'chat_wallpaper_gradient_rotation': '#FF112233'}, {'chat_inBubble': '#00112233'},
                       {'chat_inBubble': {'url': 'https://example.org'}}, {'chat_inBubble': True}):
            self.assertEqual(self.post('/submit', self.submission(colors=values)).status_code, 400)
        for text in ('WPS\nraw binary', 'chat_inBubble=-1\nchat_inBubble=-2', 'chat_inBubble=https://example.org', 'x'*65537):
            data = self.submission(); del data['colors']; data['attheme_text'] = text
            self.assertEqual(self.post('/submit', data).status_code, 400)

    def test_text_canonical_order_and_alias_optin(self):
        data = self.submission(alias='Artist', alias_public=True)
        del data['colors']
        data['attheme_text'] = 'chat_outBubble=-1\r\nchat_inBubble=#FF112233\r\n'
        ident = self.post('/submit', data).json['id']; self.approve(ident)
        theme = self.client.get(mod.PREFIX+'/'+ident).json['theme']
        expected = b'chat_inBubble=-15654349\nchat_outBubble=-1\n'
        self.assertEqual(theme['sha256'], hashlib.sha256(expected).hexdigest())
        self.assertEqual(theme['author_alias'], 'Artist')

    def test_link_slug_only_no_external_url(self):
        data = self.submission(); del data['colors']
        for slug in ('https://evil.example/theme', '../other', 'valid?token=x', 'valid#fragment', ''):
            self.assertEqual(self.post('/submit', dict(data, telegram_slug=slug)).status_code, 400)
        ident = self.post('/submit', dict(data, telegram_slug='Blue_Theme-1')).json['id']; self.approve(ident)
        theme = self.client.get(mod.PREFIX+'/'+ident).json['theme']
        self.assertEqual(theme['telegram_url'], 'https://t.me/addtheme/Blue_Theme-1')
        self.assertEqual(theme['type'], 'telegram_link')

    def test_pending_and_daily_quota(self):
        ids = [self.post('/submit', self.submission()).json['id'] for _ in range(3)]
        self.assertEqual(self.post('/submit', self.submission()).status_code, 429)
        for ident in ids: self.approve(ident)
        for _ in range(2): self.assertEqual(self.post('/submit', self.submission()).status_code, 201)
        self.assertEqual(self.post('/submit', self.submission()).status_code, 429)

    def test_schema_mime_size_origin(self):
        self.assertEqual(self.post('/submit', self.submission(license_ack=False)).status_code, 400)
        self.assertEqual(self.post('/submit', self.submission(arbitrary_secret='test')).status_code, 400)
        self.assertEqual(self.post('/submit', self.submission(alias_public='true')).status_code, 400)
        self.assertEqual(self.client.post(mod.PREFIX+'/submit', data='{}').status_code, 415)
        self.assertEqual(self.client.post(mod.PREFIX+'/submit', data='x'*131073, content_type='application/json').status_code, 413)
        self.assertEqual(self.client.post(mod.PREFIX+'/mine', json={'initData':self.auth()}, headers={'Origin':'https://evil.example'}).status_code, 403)
        self.assertEqual(self.client.post(mod.PREFIX+'/mine', json={'initData':self.auth()}, headers={'Origin':'https://cintiabot.todosobreall.tech'}).status_code, 200)
        self.assertEqual(self.client.post(mod.PREFIX+'/submit', data='{"title":"a","title":"b"}', content_type='application/json').status_code, 400)

    def test_pagination_and_private_header(self):
        for uid in range(2, 7):
            ident = self.post('/submit', self.submission(initData=self.auth(uid))).json['id']; self.approve(ident)
        first = self.client.get(mod.PREFIX+'?limit=2').json
        second = self.client.get(mod.PREFIX+'?limit=2&cursor='+first['next_cursor']).json
        self.assertEqual(len(first['themes']), 2)
        self.assertFalse({t['id'] for t in first['themes']} & {t['id'] for t in second['themes']})
        self.assertEqual(self.client.get(mod.PREFIX+'?cursor=../').status_code, 400)
        self.assertEqual(self.post('/mine', {'initData':self.auth()}).headers['Cache-Control'], 'no-store')


if __name__ == '__main__':
    unittest.main()

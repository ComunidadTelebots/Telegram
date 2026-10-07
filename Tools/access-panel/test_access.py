import tempfile
import json
import sqlite3
from contextlib import closing
import unittest
from pathlib import Path

from app import create_app


class AccessTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.time = 1800000000.0
        self.app = create_app(dict(TESTING=True, DB_PATH=str(Path(self.temp.name) / 'test.sqlite'),
                                  RELEASE_DIR=str(Path(self.temp.name) / 'releases'),
                                  OWNER_ID='100', BRIDGE_SECRET='s' * 40, CLOCK=lambda: self.time))
        self.client = self.app.test_client()

    def tearDown(self):
        self.temp.cleanup()

    def call(self, method, path, data=None, token=None, csrf=None, client=None):
        headers = {}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        if csrf:
            headers['X-CSRF-Token'] = csrf
        return (client or self.client).open('/access/api' + path, method=method, json=data, headers=headers, base_url='https://chat.todosobreall.tech')

    def bridge(self, code, uid, action='approve'):
        return self.call('POST', '/bridge/' + action, {'code': code, 'consent': True, 'user': {'id': uid, 'first_name': 'Test'}}, token='s' * 40)

    def login(self, uid=100):
        response = self.call('GET', '/auth/start').get_json()
        code = response['verification_uri'].split('access_')[1]
        self.assertEqual(self.bridge(code, uid).status_code, 200)
        result = self.call('POST', '/auth/poll', {'login_token': response['login_token']})
        self.assertEqual(result.status_code, 200)
        return result.get_json()['csrf_token']

    def pair(self, product='android', channel='dev'):
        response = self.call('POST', '/device/start', {'product': product, 'channel': channel, 'package': 'org.comunidadtelebots.' + channel, 'version': '1.0.0'})
        self.assertEqual(response.status_code, 200)
        return response.get_json()

    def grant(self, uid, channels, product='android', csrf=None):
        return self.call('PUT', '/admin/grants', {'user_id': uid, 'product': product, 'channels': channels}, csrf=csrf)

    def test_private_data_requires_owner(self):
        self.assertEqual(self.call('GET', '/admin/users').status_code, 401)
        csrf = self.login(200)
        self.assertEqual(self.call('GET', '/admin/users').status_code, 403)
        self.assertEqual(self.grant(200, ['dev'], csrf=csrf).status_code, 403)

    def test_bridge_requires_secret_and_cannot_spoof_cookie_login(self):
        item = self.pair()
        self.assertEqual(self.call('POST', '/bridge/approve', {'code': item['user_code'], 'user': {'id': 100}}).status_code, 401)
        self.assertEqual(self.call('POST', '/auth/telegram', {'id': 100}).status_code, 405)

    def test_new_user_denied_then_granted_and_revoked_without_resurrection(self):
        csrf = self.login()
        item = self.pair()
        self.assertEqual(self.bridge(item['user_code'], 200, 'inspect').status_code, 200)
        self.assertEqual(self.bridge(item['user_code'], 200).status_code, 403)
        self.assertEqual(self.grant(200, ['dev'], csrf=csrf).status_code, 200)
        self.assertEqual(self.bridge(item['user_code'], 200).status_code, 200)
        approved = self.call('POST', '/device/poll', {}, token=item['device_token']).get_json()
        session = approved['session_token']
        self.assertEqual(self.call('POST', '/session/check', {}, token=session).status_code, 200)
        self.assertEqual(self.grant(200, [], csrf=csrf).status_code, 200)
        self.assertEqual(self.call('POST', '/session/check', {}, token=session).status_code, 403)
        self.grant(200, ['dev'], csrf=csrf)
        self.assertEqual(self.call('POST', '/session/check', {}, token=session).status_code, 403)

    def test_product_and_channel_grants_do_not_cross(self):
        csrf = self.login()
        self.grant(200, ['dev'], csrf=csrf)
        for product, channel in [('react', 'dev'), ('android', 'beta')]:
            item = self.pair(product, channel)
            self.assertEqual(self.bridge(item['user_code'], 200).status_code, 403)

    def test_csrf_and_cross_origin_block_mutations(self):
        self.login()
        self.assertEqual(self.grant(200, ['dev']).status_code, 403)
        result = self.client.get('/access/api/auth/start', headers={'Origin': 'https://evil.example'}, base_url='https://chat.todosobreall.tech')
        self.assertEqual(result.status_code, 403)

    def test_web_token_is_bound_to_original_browser_and_one_time(self):
        challenge = self.call('GET', '/auth/start').get_json()
        self.bridge(challenge['verification_uri'].split('access_')[1], 100)
        other = self.app.test_client()
        self.assertEqual(self.call('POST', '/auth/poll', {'login_token': challenge['login_token']}, client=other).status_code, 403)
        self.assertEqual(self.call('POST', '/auth/poll', {'login_token': challenge['login_token']}).status_code, 200)
        self.assertEqual(self.call('POST', '/auth/poll', {'login_token': challenge['login_token']}).status_code, 403)

    def test_expiry_blocks_device_and_presence_is_recent(self):
        self.login()
        item = self.pair()
        self.bridge(item['user_code'], 100)
        session = self.call('POST', '/device/poll', {}, token=item['device_token']).get_json()['session_token']
        users = self.call('GET', '/admin/users').get_json()['users']
        self.assertTrue(users[0]['sessions'][0]['active'])
        self.time += 181
        self.assertFalse(self.call('GET', '/admin/users').get_json()['users'][0]['sessions'][0]['active'])
        self.time += 120
        self.assertEqual(self.call('POST', '/session/check', {}, token=session).status_code, 403)

    def test_stable_public_and_package_validation(self):
        item = self.pair(channel='stable')
        self.assertEqual(self.bridge(item['user_code'], 200).status_code, 200)
        self.assertEqual(self.call('POST', '/device/start', {'product': 'android', 'channel': 'dev', 'package': 'fake'}).status_code, 400)
        self.assertEqual(self.call('GET', '/react/authorize?channel=stable').status_code, 200)

    def test_react_cookie_authorization_and_revocation(self):
        csrf = self.login()
        self.grant(200, ['beta'], product='react', csrf=csrf)
        self.login(200)
        self.assertEqual(self.call('GET', '/react/authorize?channel=dev').status_code, 403)
        self.assertEqual(self.call('GET', '/react/authorize?channel=beta').status_code, 200)
        csrf = self.login()
        users = self.call('GET', '/admin/users').get_json()['users']
        target = next(u for u in users if u['id'] == '200')
        self.assertEqual(target['sessions'][0]['product'], 'react')
        self.assertTrue(target['sessions'][0]['active'])
        self.assertEqual(self.grant(200, [], product='react', csrf=csrf).status_code, 200)

    def update_manifest(self, file_channel='stable', **changes):
        value = dict(product='android', channel=file_channel, package='org.comunidadtelebots.' + file_channel,
                     version_name='2.0.0', version_code=20, confirmed=True, url='https://t.me/c/4211289901/123')
        value.update(changes)
        folder = Path(self.app.config['RELEASE_DIR']) / 'android'
        folder.mkdir(parents=True, exist_ok=True)
        (folder / (file_channel + '.json')).write_text(json.dumps(value), encoding='utf-8')

    def update(self, channel='stable', code=10, token=None):
        return self.call('POST', '/android/update', {'channel': channel, 'package': 'org.comunidadtelebots.' + channel, 'version_code': code}, token=token)

    def test_update_missing_is_unavailable_not_current(self):
        self.assertEqual(self.update().get_json(), {'status': 'unavailable'})
        self.update_manifest()
        self.assertEqual(self.update().get_json()['status'], 'available')
        for code in (20, 21):
            self.assertEqual(self.update(code=code).get_json()['status'], 'current')
            self.assertNotIn('url', self.update(code=code).get_json())

    def test_update_optional_integrity_and_compatibility_metadata(self):
        metadata = dict(size_bytes=123456, min_sdk=21, abis=['arm64-v8a'], sha256='a'*64,
                        signer_sha256=['b'*64], release_notes='Real release notes')
        self.update_manifest(**metadata)
        result = self.update().get_json()
        for key, value in metadata.items():
            self.assertEqual(result[key], value)
        self.assertEqual(result['package'], 'org.comunidadtelebots.stable')
        self.assertEqual(result['channel'], 'stable')
        self.update_manifest(size_bytes=-1, min_sdk=True, abis=['../secret'], sha256='not a hash',
                             signer_sha256=['not a certificate'], release_notes='x'*2001)
        result = self.update().get_json()
        self.assertEqual(result['status'], 'available')
        for key in metadata:
            self.assertNotIn(key, result)

    def test_update_invalid_manifest_never_exposes_link(self):
        for changes in ({'confirmed': False}, {'package': 'org.comunidadtelebots.dev'}, {'channel': 'dev'},
                        {'url': 'https://evil.example/file.apk'}, {'url': 'https://t.me/c/123/1?token=x'}, {'version_code': True}):
            self.update_manifest(**changes)
            self.assertEqual(self.update().get_json(), {'status': 'unavailable'})

    def test_update_requires_same_product_branch_and_live_session(self):
        self.update_manifest('dev')
        self.assertEqual(self.update('dev').status_code, 403)
        for product, channel in [('react', 'dev'), ('android', 'beta'), ('android', 'dev')]:
            pair = self.pair(product, channel)
            self.bridge(pair['user_code'], 100)
            token = self.call('POST', '/device/poll', {}, token=pair['device_token']).get_json()['session_token']
            self.assertEqual(self.update('dev', token=token).status_code, 200 if product == 'android' and channel == 'dev' else 403)
        self.time += 301
        self.assertEqual(self.update('dev', token=token).status_code, 403)

    def test_update_revoked_session_rejected(self):
        csrf = self.login()
        self.grant(200, ['dev'], csrf=csrf)
        pair = self.pair()
        self.bridge(pair['user_code'], 200)
        token = self.call('POST', '/device/poll', {}, token=pair['device_token']).get_json()['session_token']
        self.update_manifest('dev')
        self.assertEqual(self.update('dev', token=token).status_code, 200)
        self.grant(200, [], csrf=csrf)
        self.assertEqual(self.update('dev', token=token).status_code, 403)

    def test_country_is_optional_and_requires_auth_csrf_consent_valid_iso(self):
        self.assertEqual(self.call('POST', '/profile/country', {'country':'ES','consent':True}).status_code, 401)
        csrf = self.login(200)
        self.assertIsNone(self.call('GET', '/profile').get_json()['country'])
        self.assertEqual(self.call('POST', '/profile/country', {'country':'ES','consent':True}).status_code, 403)
        for data in ({'country':'XX','consent':True}, {'country':'ES','consent':False}, {'country': {'gps':1},'consent':True}):
            self.assertEqual(self.call('POST','/profile/country',data,csrf=csrf).status_code,400)
        self.assertEqual(self.call('POST','/profile/country',{'country':'ES','consent':True},csrf=csrf).status_code,200)
        self.assertEqual(self.call('GET','/profile').get_json()['country'],'ES')
        self.assertEqual(self.call('POST','/profile/country',{'country':None},csrf=csrf).status_code,200)
        self.assertIsNone(self.call('GET','/profile').get_json()['country'])

    def test_country_aggregates_owner_only_and_no_individual_country(self):
        for uid in (200,201,202):
            csrf=self.login(uid)
            self.call('POST','/profile/country',{'country':'ES','consent':True},csrf=csrf)
        self.assertEqual(self.call('GET','/admin/countries').status_code,403)
        csrf=self.login(203)
        self.call('POST','/profile/country',{'country':'FR','consent':True},csrf=csrf)
        self.login()
        data=self.call('GET','/admin/countries').get_json()
        self.assertEqual(data['countries'],[{'country':'ES','count':3}])
        self.assertEqual(data['suppressed'],1)
        for user in self.call('GET','/admin/users').get_json()['users']:
            self.assertNotIn('country',user)

    def test_export_and_delete_only_own_profile_and_revoke_device(self):
        admin=self.login()
        self.grant(200,['dev'],csrf=admin)
        pair=self.pair()
        self.bridge(pair['user_code'],200)
        token=self.call('POST','/device/poll',{},token=pair['device_token']).get_json()['session_token']
        csrf=self.login(200)
        self.call('POST','/profile/country',{'country':'ES','consent':True},csrf=csrf)
        exported=self.call('GET','/profile/export').get_json()
        self.assertEqual(exported['profile']['id'],'200')
        self.assertEqual(exported['country'][0]['country'],'ES')
        self.assertNotIn(token,json.dumps(exported))
        self.assertNotIn('hash',json.dumps(exported))
        self.assertEqual(self.call('POST','/profile/delete',{'confirm':'delete_access_profile'}).status_code,403)
        self.assertEqual(self.call('POST','/profile/delete',{'confirm':'delete_access_profile'},csrf=csrf).status_code,200)
        self.assertEqual(self.call('POST','/session/check',{},token=token).status_code,403)
        self.assertEqual(self.call('GET','/profile').status_code,401)
        self.login()
        self.assertEqual([u['id'] for u in self.call('GET','/admin/users').get_json()['users']],['100'])
        self.assertEqual(self.call('GET','/admin/countries').get_json()['countries'],[])

    def test_public_privacy_and_draft_terms_do_not_require_login(self):
        for path in ('/access/privacy','/access/terms'):
            response=self.client.get(path)
            self.assertEqual(response.status_code,200)
            self.assertTrue(response.content_type.startswith('text/html'))
            self.assertIn("frame-ancestors 'none'",response.headers['Content-Security-Policy'])
            response.close()

    def test_inspection_does_not_collect_identity_before_consent(self):
        self.login()
        pair=self.pair()
        response=self.call('POST','/bridge/inspect',{'code':pair['user_code']},token='s'*40)
        self.assertEqual(response.status_code,200)
        self.assertEqual([u['id'] for u in self.call('GET','/admin/users').get_json()['users']],['100'])
        with closing(sqlite3.connect(self.app.config['DB_PATH'])) as db:
            self.assertEqual(db.execute('SELECT COUNT(*) FROM requests').fetchone()[0], 0)
            self.assertEqual(db.execute("SELECT COUNT(*) FROM users WHERE id='200'").fetchone()[0], 0)
        self.assertEqual(self.call('POST','/bridge/approve',{'code':pair['user_code'],'user':{'id':200}},token='s'*40).status_code,400)
        response=self.bridge(pair['user_code'],200)
        self.assertEqual(response.status_code,403)
        self.assertTrue(response.get_json()['registered'])
        self.assertEqual(self.call('POST','/device/poll',{},token=pair['device_token']).get_json()['status'],'pending')
        self.assertIn('200',[u['id'] for u in self.call('GET','/admin/users').get_json()['users']])


if __name__ == '__main__':
    unittest.main()

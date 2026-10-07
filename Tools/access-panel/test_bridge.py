import os
import unittest
from unittest.mock import Mock, patch

from moonbot_access_bridge import handle_access_update


class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.bot = Mock(bot_username='CintiaBot')
        self.code = 'a' * 24
        self.message = {'message': {'text': '/start access_' + self.code, 'from': {'id': 100}, 'chat': {'id': 100, 'type': 'private'}}}
        self.environment = patch.dict(os.environ, {'ACCESS_BOT_USERNAME': 'CintiaBot', 'ACCESS_BRIDGE_URL': 'https://chat.todosobreall.tech/access/api/bridge', 'ACCESS_BRIDGE_SECRET': 's' * 40})
        self.environment.start()

    def tearDown(self):
        self.environment.stop()

    @patch('moonbot_access_bridge.requests.post')
    def test_start_only_inspects_and_requests_consent(self, post):
        post.return_value.status_code = 200
        post.return_value.json.return_value = {'allowed': True, 'kind': 'device', 'product': 'android', 'channel': 'dev'}
        self.assertTrue(handle_access_update(self.bot, self.message))
        self.assertTrue(post.call_args.args[0].endswith('/inspect'))
        self.assertFalse(post.call_args.kwargs['allow_redirects'])
        self.assertEqual(post.call_args.kwargs['json'], {'code': self.code})
        self.assertIn('reply_markup', self.bot.api_call.call_args.args[1])

    @patch('moonbot_access_bridge.requests.post')
    def test_other_bot_and_group_cannot_bridge(self, post):
        self.bot.bot_username = 'OtherBot'
        self.assertFalse(handle_access_update(self.bot, self.message))
        self.bot.bot_username = 'CintiaBot'
        self.message['message']['chat']['type'] = 'group'
        self.assertFalse(handle_access_update(self.bot, self.message))
        post.assert_not_called()

    @patch('moonbot_access_bridge.requests.post')
    def test_callback_requires_private_sender_identity(self, post):
        event = {'callback_query': {'id': 'cb', 'data': 'access_ok:' + self.code, 'from': {'id': 200}, 'message': {'chat': {'id': 100, 'type': 'private'}, 'message_id': 1}}}
        self.assertTrue(handle_access_update(self.bot, event))
        post.assert_not_called()


if __name__ == '__main__':
    unittest.main()

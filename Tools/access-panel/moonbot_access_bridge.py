"""Copy into Moonbot core/, call handle_access_update(self, u) before other handlers.

Uses the existing poller. Only verified updates of the configured identity bot are
accepted; /start alone NEVER approves a device/browser. The user must tap consent.
"""
import os
import re
from urllib.parse import urlsplit

import requests


def handle_access_update(bot, update):
    username = os.environ.get('ACCESS_BOT_USERNAME', 'CintiaBot')
    if getattr(bot, 'bot_username', '').lower() != username.lower():
        return False
    message = update.get('message') or {}
    callback = update.get('callback_query') or {}
    code, approve = None, False
    match = re.fullmatch(r'/start(?:@[A-Za-z0-9_]+)? access_([a-f0-9]{24})', message.get('text', ''))
    if match and message.get('chat', {}).get('type') == 'private':
        code, user = match[1], message.get('from', {})
        chat = message['chat']['id']
    elif re.fullmatch(r'access_ok:[a-f0-9]{24}', callback.get('data', '')):
        code, approve, user = callback['data'].split(':')[1], True, callback.get('from', {})
        cb_message = callback.get('message') or {}
        if cb_message.get('chat', {}).get('type') != 'private':
            return True
        chat = cb_message['chat']['id']
    else:
        return False
    if str(chat) != str(user.get('id')) or user.get('is_bot'):
        return True
    base = os.environ.get('ACCESS_BRIDGE_URL', '').rstrip('/')
    secret = os.environ.get('ACCESS_BRIDGE_SECRET', '')
    parsed = urlsplit(base)
    # Production uses HTTPS; never leak the bridge credential to arbitrary redirects.
    if parsed.scheme != 'https' or parsed.username or parsed.password or len(secret) < 32:
        bot.api_call('sendMessage', {'chat_id': chat, 'text': 'El acceso privado no está configurado todavía.'})
        return True
    try:
        payload = {'code': code}
        if approve:
            payload.update(consent=True, user={k: user[k] for k in ('id', 'first_name', 'username') if k in user})
        response = requests.post(base + ('/approve' if approve else '/inspect'),
                                 headers={'Authorization': 'Bearer ' + secret},
                                 json=payload,
                                 timeout=(5, 10), allow_redirects=False)
        if response.status_code != 200:
            text = 'Solicitud registrada. Todavía no tienes permiso para esta rama. El administrador puede autorizarla desde el panel; después vuelve a solicitar acceso.' if response.status_code == 403 else 'Solicitud caducada o ya utilizada. Genera otra desde la aplicación o el panel.'
            bot.api_call('sendMessage', {'chat_id': chat, 'text': text})
        else:
            data = response.json()
            if approve:
                bot.api_call('sendMessage', {'chat_id': chat, 'text': 'Acceso confirmado. Vuelve a la aplicación o al navegador donde lo solicitaste.'})
                bot.api_call('editMessageReplyMarkup', {'chat_id': chat, 'message_id': cb_message['message_id'], 'reply_markup': {'inline_keyboard': []}})
            else:
                target = 'el panel web de acceso' if data['kind'] == 'web' else data['product'] + ' · ' + data['channel']
                bot.api_call('sendMessage', {'chat_id': chat,
                    'text': '¿Has solicitado tú iniciar sesión en ' + target + '?\nCódigo: ' + code + '\nConfirma únicamente si coincide con tu pantalla. No apruebes enlaces enviados por otra persona.\n\nAl confirmar enviarás a Todosobrealltech tu ID, nombre y usuario para gestionar permisos. Al usar una rama privada se registra la rama, versión cuando se informa y última comprobación de acceso; las versiones anteriores pueden continuar en segundo plano. Este control no envía GPS ni chats. Si no tienes permiso, se registrará tu solicitud, sin concederte acceso.\nDatos y conservación: https://chat.todosobreall.tech/access/privacy',
                    'link_preview_options': {'is_disabled': True},
                    'reply_markup': {'inline_keyboard': [[{'text': 'Confirmar y enviar mis datos de acceso', 'callback_data': 'access_ok:' + code}]]}})
    except (requests.RequestException, ValueError, KeyError):
        bot.api_call('sendMessage', {'chat_id': chat, 'text': 'El servicio de acceso no responde. Inténtalo de nuevo desde tu aplicación.'})
    finally:
        if callback.get('id'):
            bot.api_call('answerCallbackQuery', {'callback_query_id': callback['id']})
    return True

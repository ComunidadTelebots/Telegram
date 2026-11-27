"""Rebuild Aurora assets from the pinned local React source (no network at runtime)."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
REACT = ROOT.parent / 'telegram-react'
ASSETS = ROOT / 'TMessagesProj/src/main/assets'
REACT_REVISION = '55ff2366823eba3d2a2b7f0f599a186d9a39c9a3'


def tokens(midnight=False):
    css = (REACT / 'src/designs/aurora.css').read_text(encoding='utf-8')
    values = dict(re.findall(r'--([\w-]+):\s*(#[0-9a-fA-F]{6});', css))
    if midnight:
        css += (REACT / 'src/designs/aurora-midnight.css').read_text(encoding='utf-8')
        values.update(dict(re.findall(r'--([\w-]+):\s*(#[0-9a-fA-F]{6});', css)))
    def val(key):
        return int(values[key][1:], 16) | 0xff000000
    accent = val('aurora-accent')
    bg = val('design-page-background' if midnight else 'aurora-bg')
    panel = val('design-sidebar-background' if midnight else 'aurora-sidebar')
    incoming = val('message-in-background' if midnight else 'aurora-bubble-in')
    outgoing = val('message-out-background' if midnight else 'aurora-bubble-out')
    text = val('aurora-text-primary')
    secondary = val('aurora-text-secondary')
    out_text = val('message-out-color') if midnight else text
    colors = {}
    def put(value, keys):
        for key in keys.split():
            colors[key] = value
    put(bg, 'windowBackgroundGray chat_wallpaper chats_actionIcon chats_unreadCounterText chat_inReactionButtonTextSelected chat_outReactionButtonTextSelected')
    put(panel, 'windowBackgroundWhite actionBarDefault actionBarDefaultArchived actionBarActionModeDefault dialogBackground chats_menuBackground chats_menuTopBackground chat_messagePanelBackground chat_emojiPanelBackground chat_topPanelBackground')
    put(text, 'windowBackgroundWhiteBlackText dialogTextBlack actionBarDefaultTitle actionBarDefaultIcon actionBarDefaultSearch actionBarActionModeDefaultIcon chats_name chats_nameMessage chats_menuItemText profile_title chat_messageTextIn chat_messagePanelText chat_emojiPanelIconSelected')
    put(out_text, 'chat_messageTextOut')
    put(secondary, 'windowBackgroundWhiteGrayText windowBackgroundWhiteGrayText2 windowBackgroundWhiteGrayText3 windowBackgroundWhiteGrayText4 windowBackgroundWhiteGrayIcon windowBackgroundWhiteHintText dialogTextGray dialogTextGray2 dialogIcon actionBarDefaultSubtitle actionBarDefaultSearchPlaceholder chats_message chats_date chats_message_threeLines chat_inTimeText chat_inTimeSelectedText chat_messagePanelHint chat_messagePanelIcons chat_serviceText actionBarTabUnactiveText')
    put(accent, 'windowBackgroundWhiteBlueText windowBackgroundWhiteBlueText2 windowBackgroundWhiteBlueText3 windowBackgroundWhiteBlueText4 windowBackgroundWhiteBlueText5 windowBackgroundWhiteBlueText6 windowBackgroundWhiteBlueText7 windowBackgroundWhiteLinkText windowBackgroundWhiteBlueIcon dialogTextBlue dialogTextBlue2 dialogTextBlue4 dialogButton checkbox radioBackgroundChecked switchTrackChecked chats_actionBackground chats_unreadCounter chats_nameMessage_threeLines chats_verifiedBackground chats_draft chats_onlineCircle actionBarTabActiveText actionBarTabLine chat_messageLinkIn chat_messageLinkOut chat_inReplyLine chat_outReplyLine chat_inReplyNameText chat_outReplyNameText chat_inViaBotNameText chat_outViaBotNameText chat_outSentCheck chat_outSentCheckRead chat_outSentCheckSelected chat_messagePanelCursor chat_messagePanelSend chat_inReactionButtonText chat_outReactionButtonText chat_inAudioProgress chat_outAudioProgress chat_goDownButtonCounterBackground')
    put(incoming, 'chat_inBubble chat_messagePanelVoiceLockBackground')
    put(outgoing, 'chat_outBubble')
    put(0xff193c36 if midnight else 0xff294f40, 'chat_outBubbleSelected')
    put(0xff20303b if midnight else 0xff303b44, 'chat_inBubbleSelected')
    put(0x24000000 | (accent & 0xffffff), 'listSelector actionBarDefaultSelector actionBarTabSelector chats_tabletSelectedOverlay chat_selectedBackground chat_inReactionButtonBackground chat_outReactionButtonBackground')
    put(0x14ffffff, 'divider chat_messagePanelShadow')
    put(0x4dffffff, 'chats_unreadCounterMuted')
    put((0x8c000000 | (out_text & 0xffffff)) if midnight else (0xb8000000 | (accent & 0xffffff)), 'chat_outTimeText chat_outTimeSelectedText')
    put(0xe621262d, 'chat_serviceBackground')
    put(0x66000000, 'chat_inBubbleShadow chat_outBubbleShadow')
    put(0, 'chat_outBubbleGradient1 chat_outBubbleGradient2 chat_outBubbleGradient3 chat_outBubbleGradientAnimated chat_wallpaper_gradient_to1 chat_wallpaper_gradient_to2 chat_wallpaper_gradient_to3')
    return colors


def main():
    revision = subprocess.check_output(['git', '-C', str(REACT), 'rev-parse', 'HEAD'], text=True).strip()
    if revision != REACT_REVISION:
        raise SystemExit('React revision differs from the reviewed design source')
    subprocess.run(['git', '-C', str(REACT), 'diff', '--exit-code', '--quiet', 'HEAD', '--',
                    'src/designs/aurora.css', 'src/designs/aurora-midnight.css'], check=True)
    for name, midnight in [('aurora', False), ('aurora-midnight', True)]:
        lines = [f'{k}={v if v < 2**31 else v - 2**32}' for k, v in sorted(tokens(midnight).items())]
        (ASSETS / (name + '.attheme')).write_text('\n'.join(lines) + '\n', encoding='utf-8')
    sys.path.insert(0, str(ROOT / 'output/aurora-20261109/tools'))
    from fontTools.ttLib import TTFont
    from fontTools.varLib.instancer import instantiateVariableFont
    source = ROOT / 'output/aurora-20261109/source/Manrope-variable.ttf'
    for name, weight in [('Regular', 400), ('SemiBold', 600)]:
        font = instantiateVariableFont(TTFont(source, recalcTimestamp=False), {'wght': weight}, inplace=False)
        font.save(ASSETS / f'fonts/aurora/Manrope-{name}.ttf')
    manifest = {'react_repository': 'https://github.com/ComunidadTelebots/telegram-react',
                'react_commit': REACT_REVISION,
                'font_source': 'https://github.com/google/fonts/tree/main/ofl/manrope',
                'font_source_sha256': hashlib.sha256(source.read_bytes()).hexdigest(),
                'fonttools': '4.68.0',
                'inputs': {str(p.relative_to(REACT)): hashlib.sha256(p.read_bytes()).hexdigest()
                           for p in [REACT / 'src/designs/aurora.css', REACT / 'src/designs/aurora-midnight.css']}}
    (ASSETS / 'fonts/aurora/provenance.json').write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()

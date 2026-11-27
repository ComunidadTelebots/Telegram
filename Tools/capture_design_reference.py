"""Capture synthetic native style previews; never applies a skin or opens user chats."""
import hashlib, json, pathlib, re, subprocess, time
ROOT = pathlib.Path(__file__).resolve().parents[1]
OUT = ROOT / 'output/design-reference/2026-11-06'
if (OUT / 'manifest.json').exists():
    raise SystemExit('Dated reference already exists. Choose a new date instead of overwriting it.')
OUT.mkdir(parents=True, exist_ok=True)
ADB = pathlib.Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
SRC = ROOT / 'TMessagesProj/src/main/java/org/telegram/ui/skins'
def adb(*args):
    return subprocess.check_output([str(ADB), '-s', 'emulator-5554', *args])
styles = []
for cls in re.findall(r'skins.add\(new (\w+)\(\)\)', (SRC/'SkinManager.java').read_text()):
    path = SRC/'skins'/f'{cls}.java'
    source = path.read_text(encoding='utf-8')
    def value(method):
        match = re.search(method+r'\(\)\s*\{\s*return\s+([^;]+);', source)
        return match.group(1).strip('"') if match else None
    sid = value('getId')
    name = value('getDisplayName') or ('Android '+value('getVariantDisplayName'))
    filename = sid.replace(':','-')+'.png'
    adb('shell','am','start','-n','org.comunidadtelebots.dev/org.telegram.ui.ClientDesignPreviewActivity','--es','skin',sid)
    time.sleep(12 if not styles else 2)
    data = adb('exec-out','screencap','-p')
    (OUT/filename).write_bytes(data)
    styles.append(dict(id=sid,name=name,image=filename,radius=value('getBubbleRadiusDP'),tail=value('hasBubbleTail'),avatar=value('getAvatarRadiusFactor'),dividers=value('showChatDividers'),source=str(path.relative_to(ROOT)).replace('\\','/'),source_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),image_sha256=hashlib.sha256(data).hexdigest()))
    adb('shell','input','keyevent','4')
    adb('shell','input','keyevent','4')
    print(sid, flush=True)
(OUT/'manifest.json').write_text(json.dumps({'date':'2026-11-06','source_head':'87e98351b','coverage':'Synthetic native previews, not all application screens. APK built 2026-10-27; source parameters read 2026-11-06.','styles':styles},ensure_ascii=False,indent=2),encoding='utf-8')
from PIL import Image, ImageDraw
sheet=Image.new('RGB',(1000,4*590),'#e8edf3')
draw=ImageDraw.Draw(sheet)
for i,s in enumerate(styles):
    im=Image.open(OUT/s['image']); im.thumbnail((245,555))
    x=(i%4)*250; y=(i//4)*590
    sheet.paste(im,(x,y+25)); draw.text((x+5,y+5),s['name'],fill='black')
sheet.save(OUT/'contact-sheet.jpg')

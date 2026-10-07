"""Read-only checks: five channel resources, source preservation and adaptive safe circle."""
from pathlib import Path
import hashlib,json,xml.etree.ElementTree as ET
from PIL import Image
root=Path(__file__).resolve().parents[2]
manifest=json.loads((root/'Tools/branding/channels/manifest.json').read_text(encoding='utf8'))
assert hashlib.sha256((root/manifest['source']).read_bytes()).hexdigest()==manifest['source_sha256']
assert set(manifest['images'])=={'dev','alfa','beta','rc','stable'}
hashes=set()
for channel,item in manifest['images'].items():
 p=root/item['path'];digest=hashlib.sha256(p.read_bytes()).hexdigest();assert digest==item['sha256'];hashes.add(digest)
 im=Image.open(p).convert('RGB');w,h=im.size;assert w==h and w>=512
 res=p.parents[1];inset=ET.parse(res/'drawable/comunidad_telebots_foreground.xml').getroot()
 assert inset.attrib['{http://schemas.android.com/apk/res/android}inset']=='20%'
 ET.parse(res/'mipmap-anydpi-v26/comunidad_telebots_launcher.xml')
 # Both emblem colors and lettering must fit the adaptive icon's guaranteed safe circle.
 max_radius=0
 for y in range(h):
  for x in range(w):
   r,g,b=im.getpixel((x,y))
   if g>150 and b>100:
    max_radius=max(max_radius,((.6*(x/w-.5))**2+(.6*(y/h-.5))**2)**.5)
 assert max_radius<=33/108,(channel,max_radius)
 print(channel,im.size,'safe circle OK')
assert len(hashes)==5
print('Original unchanged; five distinct channel PNGs and adaptive XML validated')

"""Explicit staging installer: root supplies the reviewed Hub web directory. No deployment."""
from pathlib import Path
import argparse, shutil
p=argparse.ArgumentParser();p.add_argument('web_directory',type=Path);a=p.parse_args()
hub=a.web_directory/'hub.html'
text=hub.read_text(encoding='utf-8')
if 'id="v-usuario"' not in text or '</body>' not in text:
 raise SystemExit('Unsupported Hub; no files changed')
source=Path(__file__).parent
for name in ('community-themes.js','community-themes.css'):
 shutil.copyfile(source/name,a.web_directory/name)
if 'src="community-themes.js"' not in text:
 text=text.replace('</body>','<link rel="stylesheet" href="community-themes.css">\n<script src="community-themes.js"></script>\n</body>',1)
 hub.write_text(text,encoding='utf-8')

"""Produce the dated design reference from genuine, synthetic native previews."""
import json, pathlib
from reportlab.pdfgen import canvas
from reportlab.lib.colors import HexColor
from reportlab.lib.utils import ImageReader
from reportlab.platypus import Paragraph
from reportlab.lib.styles import ParagraphStyle
from PIL import Image
ROOT=pathlib.Path(__file__).resolve().parents[1]
DATA=ROOT/'output/design-reference/2026-11-06'
manifest=json.loads((DATA/'manifest.json').read_text(encoding='utf-8'))
OUT=ROOT/'output/pdf/comunidad-telebots-referencia-visual-2026-11-06.pdf'
if OUT.exists():
    raise SystemExit('Dated reference already exists. Create a new dated revision.')
OUT.parent.mkdir(exist_ok=True,parents=True)
c=canvas.Canvas(str(OUT),pagesize=(960,720))
c.setTitle('Comunidad Telebots | Referencia visual fija | 2026-11-06')
style=ParagraphStyle('body',fontName='Helvetica',fontSize=14,leading=21,textColor=HexColor('#37465a'))
def txt(s,x,y,w=470):
    p=Paragraph(s,style); _,h=p.wrap(w,600); p.drawOn(c,x,y-h)
def base(n,tag,title):
    c.setFillColor(HexColor('#f3f6fa')); c.rect(0,0,960,720,fill=1,stroke=0)
    c.setFillColor(HexColor('#0a213e')); c.setFont('Helvetica-Bold',11); c.drawString(42,681,'COMUNIDAD TELEBOTS / ANDROID')
    c.setFillColor(HexColor('#128c91')); c.drawRightString(918,681,tag)
    c.setFillColor(HexColor('#0a213e')); c.setFont('Helvetica-Bold',29); c.drawString(42,628,title)
    c.setFont('Helvetica',10); c.drawString(42,26,'Referencia 01 | 06 noviembre 2026 | Conservar esta edición')
    c.drawRightString(918,26,f'{n:02d} / 19')
base(1,'BASE VISUAL','Así se ve el catálogo actual')
txt('16 estilos, una referencia estable',42,570)
txt('Esta edición conserva las vistas previas nativas de los estilos de Comunidad Telebots. Es la base que debe consultarse antes de modificar su apariencia.',42,520)
txt('<b>Qué queda documentado</b><br/>Forma de burbujas y avatares, separadores, ubicación de navegación y parámetros de animación.',42,420)
txt('<b>Qué no demuestra</b><br/>No es una captura de todos los flujos autenticados ni una reproducción exacta de los clientes iOS, macOS o web. Los nombres identifican los estilos de esta app.',42,310)
txt('Capturas: Pixel, datos de ejemplo, 06/11/2026.<br/>APK local: 27/10/2026. Parámetros del código: 06/11/2026.<br/>Base del código: 87e98351b, con cambios locales.',42,165,530)
c.drawImage(str(DATA/'android-current.png'),650,100,width=243,height=540)
c.showPage()
nav={'ios':('Inferior',56),'telegramx':('Superior',48),'unigram':('Lateral',72),'web':('Lateral',64),'webk':('Lateral',60),'macos':('Inferior',48)}
for i,s in enumerate(manifest['styles'],2):
    base(i,'ESTILO / '+s['id'],s['name'])
    # Original full screenshot retained in manifest; crop only empty OS margins for the catalog.
    im=Image.open(DATA/s['image']).crop((40,180,1040,2220))
    c.drawImage(ImageReader(im),44,74,width=247,height=504)
    placement,extent=nav.get(s['id'],('Nativa de Android',0))
    rows=[('Navegación',placement+(f' · {extent} dp' if extent else '')),('Radio de burbuja',s['radius']+' dp'),('Cola de burbuja','Sí' if s['tail']=='true' else 'No'),('Factor de avatar',s['avatar'].rstrip('f')),('Separadores','Sí' if s['dividers']=='true' else 'No')]
    y=560
    for label,val in rows:
        c.setFillColor(HexColor('#dce4ed')); c.line(340,y-43,910,y-43)
        c.setFillColor(HexColor('#52637a')); c.setFont('Helvetica',12); c.drawString(340,y,label)
        c.setFillColor(HexColor('#0a213e')); c.setFont('Helvetica-Bold',18); c.drawString(610,y,val)
        y-=68
    txt('Colores y movimiento se configuran por separado. Esta captura conserva el tema claro usado en la prueba; no impone una paleta única al usuario.',340,180,560)
    txt('Vista previa nativa con contenido de ejemplo. No representa todos los estados de conversación.',340,100,560)
    c.showPage()
base(18,'MOVIMIENTO','Animaciones documentadas')
txt('Preferencia independiente del estilo y del tema de color.',42,568,860)
for y,row in zip([490,420,350,280,210],[('Original','150 ms','48 dp'),('Suave','240 ms','32 dp'),('Rápida','120 ms','24 dp'),('Desvanecer','180 ms','0 dp'),('Sin animación','0 ms','0 dp')]):
    for x,v in zip([42,450,700],row):
        c.setFillColor(HexColor('#0a213e')); c.setFont('Helvetica-Bold',20); c.drawString(x,y,v)
txt('Valores de MotionStyle.java: duración y desplazamiento. Las páginas estáticas no acreditan la fluidez ni todos los gestos; la validación de movimiento requiere una prueba en dispositivo.',42,140,850)
c.showPage()
base(19,'CONTROL DE CAMBIOS','Conservar, comparar y versionar')
txt('<b>1. Conservar esta edición</b><br/>No sobrescribir la referencia. Crear una copia fechada para una propuesta de rediseño.',42,560,850)
txt('<b>2. No rediseñar al corregir un error</b><br/>Mantener formas, jerarquía, navegación y movimiento salvo que el usuario solicite cambiarlos. Comparar las capturas antes y después.',42,450,850)
txt('<b>3. Añadir cobertura sin inventar pantallas</b><br/>Pendientes: chats reales de cada estilo, ajustes, acceso, AMP, llamadas, estados vacíos y errores; tema oscuro, texto grande y Android 5.0. Esta edición no los declara validados.',42,335,850)
txt('<b>4. Mantener la trazabilidad</b><br/>Las imágenes originales y sus huellas están en el manifiesto local. El catálogo anterior de Canva se conserva como histórico. Esta referencia no bloquea técnicamente la edición en Canva.',42,205,850)
c.showPage(); c.save()
print(OUT)

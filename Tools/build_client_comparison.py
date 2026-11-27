"""Reference images from publishers, compared with inspected local call sites."""
import pathlib,json,urllib.request,base64,html,hashlib
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'output/design-comparison/2026-11-06'
OUT.mkdir(parents=True,exist_ok=True)
refs={
'android:current':('https://telegram.org/file/400780400761/5/NUQtbBXaUtw.48273.jpg/7966009f2fbda9e53b','https://telegram.org/blog/crafting-android-design-and-more','Referencia del rediseño publicado el 09/02/2026. No acredita la versión instalada.','La base Android conserva sus pantallas y navegación nativa. Las variantes históricas no cambian su estructura.'),
'telegramx':('https://telegram.org/file/811140700/1954/UL01eOk5gqw.195810/94a6e86da2ab0abbb0','https://telegram.org/blog/telegram-x','Referencia histórica oficial de 2018: canal sin burbujas. No representa la última versión.','El cliente ofrece modos propios, incluida conversación sin burbujas. Nuestra barra Chats/Llamadas no reproduce esos modos ni su drawer.'),
'web':('https://raw.githubusercontent.com/Ajaxy/telegram-tt/master/public/screenshot.jpg','https://github.com/Ajaxy/telegram-tt','Imagen publicada por el repositorio Web A. Versión de la captura no identificada.','Hay que conservar su composición de paneles, búsqueda y carpetas. Nuestra barra lateral contiene destinos Android, no carpetas.'),
'webk':('https://raw.githubusercontent.com/morethanwords/tweb/master/public/assets/img/screenshot.jpg','https://github.com/morethanwords/tweb','Imagen publicada por el repositorio Web K. Versión de la captura no identificada.','Necesita su panel plegable, búsqueda y compositor propios. El código local solo cambia ancho lateral, radio y separadores respecto a Web A.'),
'unigram':('https://raw.githubusercontent.com/UnigramDev/Unigram/develop/Screenshots/Screen1.PNG','https://github.com/UnigramDev/Unigram','Imagen del repositorio Unigram, cliente de terceros para Windows.','La composición lista/detalle y carpetas requiere adaptación al móvil. Actualmente solo hay una barra genérica y las pantallas Android comunes.'),
'macos':('https://macos.telegram.org/img/macos_2x.png','https://macos.telegram.org/','Imagen promocional del cliente nativo macOS. Fecha visual no identificada.','Debe conservar sus columnas, búsqueda y controles propios. Ocultar etiquetas en una barra inferior Android no reproduce macOS.'),
'ios':('https://telegram.org/file/400780400003/4/-brS8MsTvQc.60648.jpg/e54cbbc810d23ac921','https://telegram.org/blog/crafting-android-design-and-more','Referencia oficial de iOS publicada el 09/02/2026.','Necesita pestañas, cabeceras y compositor propios. La implementación local conserva iconos Android y fuerza cuatro destinos.'),
'tdesktop':('https://desktop.telegram.org/img/td_laptop.png','https://desktop.telegram.org/','Imagen promocional de Telegram Desktop. Distinto del cliente nativo macOS.','El perfil local vuelve a la navegación Android. No implementa el sistema de paneles, menús ni compositor de Desktop.')}
styles=json.loads((ROOT/'output/design-reference/2026-11-06/manifest.json').read_text(encoding='utf-8'))['styles']
nav={'ios':'Inferior, 56 dp','macos':'Inferior, 48 dp','telegramx':'Superior, 48 dp','web':'Lateral, 64 dp','webk':'Lateral, 60 dp','unigram':'Lateral, 72 dp'}
pages=[]; evidence=[]
for i,s in enumerate(styles,1):
 sid=s['id']; ref=refs.get(sid); image=''; source='Código local: '+s['source']
 if ref:
  data=urllib.request.urlopen(ref[0],timeout=35).read(); path=OUT/(sid.replace(':','-')+'.image');path.write_bytes(data)
  mime='image/png' if data.startswith(b'\x89PNG') else 'image/jpeg'
  image='<img src="data:'+mime+';base64,'+base64.b64encode(data).decode()+'">'
  caption=ref[2]; target=ref[3]; source=ref[1]
  evidence.append({'style':sid,'image_url':ref[0],'source_url':ref[1],'sha256':hashlib.sha256(data).hexdigest(),'qualification':caption})
 else:
  caption='Sin referencia visual independiente verificada'
  target=('Aurora es un estilo propio. Falta definir y aprobar una identidad completa antes de fijarlo como diseño final.' if sid=='aurora' else 'Este nombre local no acredita una reproducción histórica. Hace falta fijar versión y pantallas originales antes de aprobar su diseño.' if sid!='webogram' else 'Falta contrastar el cliente histórico Webogram. Actualmente usa las pantallas y navegación Android comunes.')
  image='<p class="pending">'+html.escape(caption)+'</p>'
 local=f"Burbujas {s['radius']} dp, {'con' if s['tail']=='true' else 'sin'} cola. Avatar: factor {s['avatar'].rstrip('f')}. Separadores: {'sí' if s['dividers']=='true' else 'no'}. Navegación: {nav.get(sid,'Android nativa')}."
 pages.append(f'''<section data-document-role="page" data-label="{html.escape(s['name'])}"><h1>{html.escape(s['name'])}</h1><div class="left">{image}<p class="caption">{html.escape(caption)}</p></div><div class="right"><h2>Integrado en nuestra app</h2><p>{local}</p><h2>Diferencia que falta resolver</h2><p>{html.escape(target)}</p><h2>Regla para el diseño final</h2><p>Reproducir la estructura del cliente de referencia, con lista y conversación separadas. Verificar también menús, ajustes y movimiento.</p></div><footer><a href="{html.escape(source)}">{html.escape(source)}</a><br>Comparación estructural del código local. La imagen de referencia no es una captura de Comunidad Telebots. {i}/16</footer></section>''')
(OUT/'comparison.html').write_text('''<!doctype html><html lang="es"><meta charset="utf-8"><title>Clientes originales frente a Comunidad Telebots</title><style>body{margin:0;font-family:Arial;color:#172638}section{width:1600px;height:900px;position:relative;box-sizing:border-box;padding:48px;background:#f7f8fa;page-break-after:always}h1{font-size:48px;margin:0 0 28px}h2{font-size:25px;margin:24px 0 10px}p{font-size:24px;line-height:1.4;margin:0}.left{position:absolute;left:48px;top:140px;width:820px;height:620px}.left img{width:820px;height:530px;object-fit:contain}.caption{font-size:18px;margin-top:16px}.right{position:absolute;left:920px;top:120px;width:620px}.pending{font-size:36px;color:#6c7681;padding-top:160px}footer{position:absolute;left:48px;bottom:30px;font-size:16px;line-height:1.6;width:1500px}a{color:#15658e}</style>'''+''.join(pages)+'</html>',encoding='utf-8')
(OUT/'sources.json').write_text(json.dumps(evidence,ensure_ascii=False,indent=2),encoding='utf-8')
print('Created 16 slides,',len(evidence),'publisher images; remaining styles explicitly unverified')

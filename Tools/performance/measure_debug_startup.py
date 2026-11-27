"""Local DEV smoke measurement. Not a production/physical-device benchmark.
Force-stops and launches the chosen app; never clears its data or changes settings.
"""
import argparse, json, re, subprocess, time, statistics
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--adb',required=True);p.add_argument('--serial',required=True)
p.add_argument('--output',required=True);p.add_argument('--runs',type=int,default=3)
a=p.parse_args()
if not 1<=a.runs<=10: p.error('runs must be 1..10')
def adb(*args):return subprocess.check_output([a.adb,'-s',a.serial,*args],text=True,encoding='utf-8',errors='replace',timeout=90)
package='org.comunidadtelebots.dev'
results=[]
for _ in range(a.runs):
 adb('shell','am','force-stop',package)
 time.sleep(1)
 launch=adb('shell','am','start','-W','-n',package+'/org.telegram.messenger.DefaultIcon')
 if not re.search(r'Status:\s*ok',launch):raise RuntimeError(launch)
 timing={k:int(v) for k,v in re.findall(r'(ThisTime|TotalTime|WaitTime):\s*(\d+)',launch)}
 if 'TotalTime' not in timing:raise RuntimeError('Missing timing: '+launch)
 time.sleep(3)
 memory=adb('shell','dumpsys','meminfo',package)
 total=re.search(r'TOTAL\s+(\d+)',memory)
 timing['pss_kib']=int(total.group(1)) if total else None
 results.append(timing)
report={'serial':a.serial,'api':adb('shell','getprop','ro.build.version.sdk').strip(),'kind':'debug emulator smoke, not controlled benchmark','runs':results,'median_total_ms':statistics.median(x['TotalTime'] for x in results)}
Path(a.output).parent.mkdir(parents=True,exist_ok=True)
Path(a.output).write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))

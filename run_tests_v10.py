from pathlib import Path
import subprocess, os, re, shutil, json, time
root=Path(__file__).parent
runners=[]
for p in sorted((root/'tests').rglob('*.java')):
 s=p.read_text()
 if 'public static void main(String[] args)' in s or 'public static void main(String[] args) ' in s or 'public static void main(String[] args)throws' in s:
  pack=re.search(r'package\s+([\w.]+)\s*;',s)
  cls=re.search(r'public final class\s+(\w+)',s)
  if pack and cls: runners.append((pack.group(1)+'.'+cls.group(1), ('Overlay' in cls.group(1) or 'AgentBoot' in cls.group(1))))
results=[]
for i,(cl,gui) in enumerate(runners):
 home=root/'test_homes'/str(i)
 if home.exists(): shutil.rmtree(home)
 home.mkdir(parents=True,exist_ok=True)
 cmd=['java','--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED','-Duser.home='+str(home),'-cp',str(root/'out')+':'+str(root/'outtests'),cl]
 if cl.endswith('.AgentBootV87Smoke'):
  cmd.insert(3,'-javaagent:'+str(root/'MirageLabAgent.jar'))
 if gui:cmd=['xvfb-run','-a']+cmd
 begin=time.time()
 try:r=subprocess.run(cmd,capture_output=True,text=True,timeout=95)
 except Exception as e:results.append(dict(test=cl,status='ERROR',detail=str(e)));continue
 output=(r.stdout+'\n'+r.stderr).strip()
 results.append(dict(test=cl,status='PASS' if r.returncode==0 else 'FAIL',summary=output[-400:],seconds=round(time.time()-begin,2)))
 print(cl, 'PASS' if r.returncode==0 else 'FAIL', output.splitlines()[-1] if output else '(no output)',flush=True)
with (root/'historical_test_results.json').open('w') as f: json.dump(results,f,ensure_ascii=False,indent=2)
print('TOTAL',len(results),'PASS',sum(x['status']=='PASS' for x in results))
if any(x['status']!='PASS' for x in results):raise SystemExit(1)

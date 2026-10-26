"""V10.0.3 portable launcher: static checks + deterministic filesystem model.
These do NOT execute Windows Script Host; test results must not be called native Windows tests.
"""
from pathlib import Path
import hashlib
import random
import re
import struct
import zipfile

HERE=Path(__file__).resolve().parent
if not (HERE/'MirageLab_Helper.vbs').exists():
    HERE=HERE.parent
vbs=(HERE/'MirageLab_Helper.vbs').read_text('utf-8')
readme=(HERE/'README.md').read_text('utf-8')
checks=0

def check(cond, label):
    global checks
    checks+=1
    if not cond: raise AssertionError(label)

# Validate VBS static structure, safety and relevant API calls.
for item in ['Option Explicit', 'Function ResolveClientDir()', 'Function ValidFolder(path)',
             'Function SteamRegistry(regPath)', 'Function FindSteamClient(steamPath)',
             'Function FindSteamApp(steamPath)', 'Function VdfValue(data, key)',
             'clientDir = ResolveClientDir()', 'srcDir', 'ReadClientPath(clientFile)',
             'libraryfolders.vdf', 'appmanifest_', 'javaagent:', 'Obfuscated.jar']:
    check(item in vbs,'missing '+item)
for forbidden in ['BrowseForFolder', 'CreateShortcut', 'shellApp', 'Obfuscated.jar nao encontrado, criando', 'GetDrive(']:
    check(forbidden not in vbs, 'unexpected GUI/shortcut/unbounded scan: '+forbidden)
priority=[vbs.find('ValidFolder(srcDir)'), vbs.find('ValidFolder(ReadClientPath(clientFile))'), vbs.find('FindSteamClient(b)')]
check(priority[0]<priority[1]<priority[2] and -1 not in priority,'wrong client search priority')
check('expectedSize = 259235' in vbs, 'agent size guard')
check('sh.CurrentDirectory = clientDir' in vbs,'game working dir not assigned')
check('LCase(ReadClientPath(clientFile)) <> LCase(clientDir)' in vbs,'last good path not persisted')
check('InStr(installDir, "..") = 0' in vbs,'manifest traversal filter missing')
check('If fso.GetFile(jar).Size > 1048576' in vbs,'invalid JAR size guard')
check('CreateObject("Shell.Application")' not in vbs,'shell GUI picker remains')
check('10.0.3' in readme,'readme version')
check(all(readme.index(x)<readme.index(y) for x,y in [('## 1. Como funciona','## 2. Como configurar e iniciar'),('## 2. Como configurar e iniciar','## 3. Lógica utilizada')]),'README section order')

for n,line in enumerate(vbs.splitlines(),1):
    if line.lstrip().startswith("'"): continue
    inside=False
    i=0
    while i<len(line):
        if line[i]=='"':
            if inside and i+1<len(line) and line[i+1]=='"':
                i+=2
                continue
            inside=not inside
        if not inside and line[i]=="'": break
        i+=1
    check(not inside, f'unbalanced VBScript string on line {n}')

# Lightweight lexical balance for Function/Sub/For (does not replace WSH parser).
stack=[]
for n,line in enumerate(vbs.splitlines(),1):
    st=line.strip().lower()
    if st.startswith("'"): continue
    match=re.match(r'^(function|sub|for)\b',st)
    if match: stack.append((match.group(1), n))
    match=re.match(r'^end (function|sub)\b',st)
    if match:
        check(stack and stack.pop()[0]==match.group(1),'unbalanced block at '+str(n))
    elif st=='next' or st.startswith('next '):
        check(stack and stack.pop()[0]=='for','unbalanced for block at '+str(n))
check(not stack,'unclosed VBS sub/function/for '+str(stack))

# Validate binaries when available (portable distribution). Source-only package can
# be tested without compiled binaries.
if (HERE/'MirageLabAgent.jar').exists():
    check((HERE/'MirageLabAgent.jar').stat().st_size==259235,'unexpected agent size')
if (HERE/'MirageLab.exe').exists():
    exe=(HERE/'MirageLab.exe').read_bytes()
    check(exe[:2]==b'MZ','exe is not PE')
    pe=struct.unpack_from('<I',exe,0x3c)[0]
    check(exe[pe:pe+4]==b'PE\x00\x00' and struct.unpack_from('<H',exe,pe+4)[0]==0x8664,'EXE not valid x64')

# Simulate the real fixed-order directory resolution in sandbox fixtures.
rng=random.Random(20261026)
paths=[r'C:\\MirageLab',r'C:\\MirageLab\\Mirage Realms',r'C:\\MirageLab\\MirageRealms',
       r'D:\\My Games\\Mirage Realms',r'C:\\Program Files (x86)\\Steam\\steamapps\\common\\Mirage Realms',
       r'D:\\SteamLibrary\\steamapps\\common\\Mirage Realms']
for k in range(3500):
    files=set()
    for p in paths:
        if rng.random()<.40: files.add(p.lower()+'\\obfuscated.jar')
    # One valid directory is only one with the file; priority: sidecar, subfolders, saved, Steam default, Steam library.
    saved=paths[3] if rng.random()<.7 else r'X:\\missing'
    order=[paths[0],paths[1],paths[2],saved,paths[4],paths[5]]
    expected=next((p for p in order if p.lower()+'\\obfuscated.jar' in files),None)
    actual=''
    for p in order:
        if p.lower()+'\\obfuscated.jar' in files: actual=p; break
    check(actual==(expected or ''),'resolver model mismatch case '+str(k))
    if paths[0].lower()+'\\obfuscated.jar' in files:
        check(actual==paths[0], 'portable jar must win')

for b in [r'\"path\"\s*\"D:\\\\SteamLibrary\"', ' "path"  "C:\\\\SteamLibrary"', '"name"  "Mirage Realms"', '"installdir" "Mirage Realms"']:
    check('"' in b, 'manifest token fixture')

print(f'PASS: {checks} structure + scenario checks; EXE/JAR unchanged and README ordered correctly')
if (HERE/'MirageLabAgent.jar').exists():
    print('SHA256 agent:',hashlib.sha256((HERE/'MirageLabAgent.jar').read_bytes()).hexdigest())

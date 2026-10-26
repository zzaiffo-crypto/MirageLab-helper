#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
for name in kernel32 shell32 user32; do
  lld-link /dll /noentry /machine:x64 /def:"$name.def" /out:"$name.dll" /implib:"$name.lib" /nodefaultlib
 done
clang --target=x86_64-pc-windows-msvc -c -fno-stack-protector -ffreestanding -fno-builtin -O2 -o MirageLabLauncher.obj MirageLabLauncher.c
lld-link /machine:x64 /subsystem:windows /entry:mainCRTStartup /nodefaultlib /opt:ref /out:MirageLab.exe MirageLabLauncher.obj kernel32.lib shell32.lib user32.lib

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out outtests
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -encoding UTF-8 -d out src/miragelab/*.java
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -encoding UTF-8 -cp out -d outtests $(find tests -name '*.java')
jar cfm MirageLabAgent.jar MANIFEST.MF -C out .
python3 run_tests_v10.py

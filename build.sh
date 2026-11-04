#!/usr/bin/env sh
# 소스 전체를 컴파일해서 out/ 폴더에 넣는다. (macOS / Linux)
set -e
cd "$(dirname "$0")"
rm -rf out
mkdir -p out
javac -encoding UTF-8 --release 17 -d out $(find src -name "*.java")
echo "빌드 완료: out/"

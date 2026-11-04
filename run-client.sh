#!/usr/bin/env sh
# 클라이언트 실행. 한 노트북에서 여러 개 띄워도 된다.
cd "$(dirname "$0")"
[ -d out ] || ./build.sh
java -cp out bunsik.client.ClientMain

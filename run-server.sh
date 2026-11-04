#!/usr/bin/env sh
# 서버 실행. 포트를 바꾸려면: ./run-server.sh 6000
cd "$(dirname "$0")"
[ -d out ] || ./build.sh
java -cp out bunsik.server.ServerMain "$@"

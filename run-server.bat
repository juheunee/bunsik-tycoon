@echo off
REM Run the game server. Change port: run-server.bat 6000
cd /d "%~dp0"
if not exist out call build.bat
java -cp out bunsik.server.ServerMain %*
pause

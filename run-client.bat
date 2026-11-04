@echo off
REM Run a game client. You can open several.
cd /d "%~dp0"
if not exist out call build.bat
start "" javaw -cp out bunsik.client.ClientMain

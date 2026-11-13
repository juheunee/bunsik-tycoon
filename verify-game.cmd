@echo off
setlocal
cd /d "%~dp0"
call verify-foundation.cmd
if errorlevel 1 exit /b 1
dir /s /b BunsikTycoon\test\*.java > out\game-test-sources.txt
javac --release 21 -encoding UTF-8 -cp out -d out @out\game-test-sources.txt
if errorlevel 1 exit /b 1
for %%T in (bunsik.common.GameContractTest bunsik.chat.ChatIntegrationTest bunsik.client.NetworkClientTest bunsik.client.ScreenTest) do (
    java -Djava.awt.headless=true -cp out %%T
    if errorlevel 1 exit /b 1
)
exit /b 0

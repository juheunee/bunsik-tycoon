@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
dir /s /b BunsikTycoon\src\*.java > out\foundation-sources.txt
echo BunsikTycoon\test\bunsik\common\FoundationTest.java>> out\foundation-sources.txt
javac --release 21 -encoding UTF-8 -d out @out\foundation-sources.txt
if errorlevel 1 exit /b 1
java -cp out bunsik.common.FoundationTest
exit /b %errorlevel%

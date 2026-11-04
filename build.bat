@echo off
REM Compile all sources into out\ (Windows)
cd /d "%~dp0"
if exist out rmdir /s /q out
mkdir out
cd src
javac -encoding UTF-8 --release 17 -d ..\out bunsik\common\*.java bunsik\server\*.java bunsik\server\game\*.java bunsik\client\*.java bunsik\client\ui\*.java bunsik\client\render\*.java
if errorlevel 1 (
  cd ..
  echo BUILD FAILED
  exit /b 1
)
cd ..
echo BUILD OK: out\

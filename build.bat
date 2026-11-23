@echo off
setlocal
cd /d "%~dp0"

if exist build rmdir /s /q build
if exist dist rmdir /s /q dist
mkdir build\classes
mkdir dist

javac -encoding UTF-8 --release 21 -d build\classes src\java\mousejiggler\*.java
if errorlevel 1 (
    echo Ошибка компиляции.
    exit /b 1
)

echo Main-Class: mousejiggler.MouseJigglerApp> manifest.txt
jar cfm dist\MouseJiggler.jar manifest.txt -C build\classes .
del manifest.txt

echo @echo off> dist\run.bat
echo java -jar "%%~dp0MouseJiggler.jar">> dist\run.bat

echo #!/usr/bin/env bash> dist\run.sh
echo exec java -jar "$(dirname "$0")/MouseJiggler.jar">> dist\run.sh

echo Сборка завершена: dist\MouseJiggler.jar, dist\run.bat, dist\run.sh

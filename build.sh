#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

rm -rf build dist
mkdir -p build/classes dist

javac -encoding UTF-8 --release 21 -d build/classes src/java/mousejiggler/*.java

echo "Main-Class: mousejiggler.MouseJigglerApp" > manifest.txt
jar cfm dist/MouseJiggler.jar manifest.txt -C build/classes .
rm manifest.txt

cat > dist/run.sh <<'EOF'
#!/usr/bin/env bash
exec java -jar "$(dirname "$0")/MouseJiggler.jar"
EOF
chmod +x dist/run.sh

cat > dist/run.bat <<'EOF'
@echo off
java -jar "%~dp0MouseJiggler.jar"
EOF

echo "Сборка завершена: dist/MouseJiggler.jar, dist/run.sh, dist/run.bat"

@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo == Compilando la aplicacion web (React + TypeScript) ==
pushd frontend
if not exist node_modules call npm install --no-audit --no-fund
call npm run build || (popd & echo Error al compilar la aplicacion web & exit /b 1)
popd
echo == Compilando la API (Java 21 + Spring Boot) ==
for /f "usebackq delims=" %%j in (`powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\java.ps1"`) do set "JAVA=%%j"
for %%d in ("%JAVA%\..\..") do set "JAVA_HOME=%%~fd"
pushd backend
call ..\herramientas\apache-maven-3.9.16\bin\mvn.cmd -q -B -DskipTests package || (popd & echo Error al compilar la API & exit /b 1)
popd
echo Listo: backend\target\sigre-global.jar

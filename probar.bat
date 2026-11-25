@echo off
chcp 65001 >nul
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\bd.ps1" -Accion iniciar
for /f "usebackq delims=" %%j in (`powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\java.ps1"`) do set "JAVA=%%j"
for %%d in ("%JAVA%\..\..") do set "JAVA_HOME=%%~fd"
echo == Casos de prueba CP01 a CP26 (Anexo B) sobre la base sigre_test ==
pushd backend
call ..\herramientas\apache-maven-3.9.16\bin\mvn.cmd -B test
popd
pause

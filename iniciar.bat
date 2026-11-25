@echo off
chcp 65001 >nul
title SIGRE Global
cd /d "%~dp0"
echo.
echo  ============================================================
echo    SIGRE Global - Sistema de gestion de riesgos empresariales
echo  ============================================================
echo.
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\bd.ps1" -Accion iniciar
if errorlevel 1 (
  echo No se pudo iniciar la base de datos.
  pause
  exit /b 1
)
if not exist "backend\target\sigre-global.jar" call compilar.bat
for /f "usebackq delims=" %%j in (`powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\java.ps1"`) do set "JAVA=%%j"
if not defined JAVA (
  echo No se encontro Java 21.
  pause
  exit /b 1
)
echo.
echo  Iniciando la API y la aplicacion web en http://localhost:8090 ...
echo  (cierre esta ventana o ejecute detener.bat para apagar el sistema)
echo.
if not defined SIGRE_SIN_NAVEGADOR start "" /b powershell -NoProfile -WindowStyle Hidden -Command "for($i=0;$i -lt 90;$i++){try{Invoke-WebRequest -UseBasicParsing http://localhost:8090/api/sistema/info -TimeoutSec 2 | Out-Null; Start-Process 'http://localhost:8090'; break}catch{Start-Sleep 2}}"
"%JAVA%" -Dfile.encoding=UTF-8 -jar "backend\target\sigre-global.jar"

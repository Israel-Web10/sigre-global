@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo.
echo  Se borraran los cambios hechos durante las pruebas y se volvera a cargar
echo  el escenario de demostracion (riesgos, alertas, auditorias, bitacora...).
echo.
choice /m "Desea continuar"
if errorlevel 2 exit /b 0
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8090 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }"
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\bd.ps1" -Accion reiniciar-datos
call iniciar.bat

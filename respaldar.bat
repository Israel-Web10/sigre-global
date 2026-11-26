@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo == Respaldo de la base de datos de SIGRE Global (RNF12) ==
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\bd.ps1" -Accion respaldar
pause

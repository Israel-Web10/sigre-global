@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo Deteniendo SIGRE Global...
powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort 8090 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }"
powershell -NoProfile -ExecutionPolicy Bypass -File "scripts\bd.ps1" -Accion detener
echo Listo.
ping -n 3 127.0.0.1 >nul

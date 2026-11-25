<#
    SIGRE Global - crea los archivos de configuración local si no existen.
    Genera claves aleatorias para la base de datos de demostración y para firmar los JWT.
    Estos archivos no se suben al repositorio (.gitignore).
#>
$Raiz = Split-Path -Parent $PSScriptRoot
$Config = Join-Path $Raiz 'config'
New-Item -ItemType Directory -Force $Config | Out-Null

function Clave([int]$largo = 24) {
    $letras = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $largo
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    -join ($bytes | ForEach-Object { $letras[$_ % $letras.Length] })
}

$bd = Join-Path $Config 'bd.properties'
if (-not (Test-Path $bd)) {
    @"
# SIGRE Global - conexion a la base de datos local de demostracion (generado por scripts\configurar.ps1).
# La instancia solo escucha en localhost.
sigre.bd.host=localhost
sigre.bd.puerto=5434
sigre.bd.nombre=sigre
sigre.bd.nombre-pruebas=sigre_test
# Propietario del esquema (ejecuta las migraciones Flyway)
sigre.bd.owner=sigre_owner
sigre.bd.owner-clave=$(Clave)
# Usuario de la aplicacion (rol app_rw del informe, seccion 10.2)
sigre.bd.app=app_rw
sigre.bd.app-clave=$(Clave)
# Usuario de solo lectura (rol app_ro del informe)
sigre.bd.lectura=app_ro
sigre.bd.lectura-clave=$(Clave)
"@ | Set-Content -Encoding ascii $bd
    Write-Host '[CONFIG] Se creó config\bd.properties con claves nuevas' -ForegroundColor Green
}

$seg = Join-Path $Config 'seguridad.properties'
if (-not (Test-Path $seg)) {
    @"
# SIGRE Global - seguridad de la API (generado por scripts\configurar.ps1)
# Clave para firmar los tokens JWT (HMAC-SHA256). Cambiarla invalida las sesiones abiertas.
sigre.jwt.secreto=$(Clave 64)
# Duracion de la sesion en minutos
sigre.jwt.minutos=480
"@ | Set-Content -Encoding ascii $seg
    Write-Host '[CONFIG] Se creó config\seguridad.properties con una clave JWT nueva' -ForegroundColor Green
}

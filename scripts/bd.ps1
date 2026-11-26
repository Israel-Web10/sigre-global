<#
  SIGRE Global - Administración de la base de datos local (PostgreSQL 17).

  Crea y controla una instancia propia de PostgreSQL para el proyecto, separada de la
  instalación del equipo. Escucha solo en localhost, en el puerto de config\bd.properties.

  Uso:  powershell -ExecutionPolicy Bypass -File scripts\bd.ps1 -Accion iniciar
        Acciones: iniciar | detener | estado | reiniciar-datos | consola | respaldar
#>
param(
    [ValidateSet('iniciar', 'detener', 'estado', 'reiniciar-datos', 'consola', 'respaldar')]
    [string]$Accion = 'iniciar'
)

$ErrorActionPreference = 'Stop'
$Raiz = Split-Path -Parent $PSScriptRoot

# ---------------------------------------------------------------- configuración
& (Join-Path $PSScriptRoot 'configurar.ps1')
$Cfg = @{}
Get-Content (Join-Path $Raiz 'config\bd.properties') | ForEach-Object {
    if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $Cfg[$Matches[1]] = $Matches[2].Trim() }
}
$Puerto = $Cfg['sigre.bd.puerto']

# Carpeta de datos fuera de la ruta del proyecto (evita problemas con espacios y la letra ñ)
$Datos = Join-Path $env:LOCALAPPDATA 'SIGRE-Global\pgdata'
$Log   = Join-Path $env:LOCALAPPDATA 'SIGRE-Global\postgresql.log'

# ---------------------------------------------------------------- binarios de PostgreSQL
$Bin = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin\pg_ctl.exe' -ErrorAction SilentlyContinue |
       Sort-Object { [int]($_.Directory.Parent.Name) } -Descending | Select-Object -First 1
if (-not $Bin) { throw 'No se encontró PostgreSQL en C:\Program Files\PostgreSQL. Instale PostgreSQL 16 o superior.' }
$BinDir = $Bin.DirectoryName
$PgCtl  = Join-Path $BinDir 'pg_ctl.exe'
$InitDb = Join-Path $BinDir 'initdb.exe'
$Psql   = Join-Path $BinDir 'psql.exe'

function Escribir($texto, $color = 'Cyan') { Write-Host "[BD] $texto" -ForegroundColor $color }

function Psql-Owner([string]$base, [string]$sql) {
    $env:PGPASSWORD = $Cfg['sigre.bd.owner-clave']
    & $Psql -h localhost -p $Puerto -U $Cfg['sigre.bd.owner'] -d $base -v ON_ERROR_STOP=1 -q -t -A -c $sql
    if ($LASTEXITCODE -ne 0) { throw "Error al ejecutar SQL en $base" }
}

function Esta-Activa {
    & $PgCtl -D $Datos status *> $null
    return ($LASTEXITCODE -eq 0)
}

function Inicializar {
    Escribir "Creando la instancia de PostgreSQL en $Datos"
    New-Item -ItemType Directory -Force (Split-Path $Datos) | Out-Null
    $pw = Join-Path $env:TEMP ('sigre_pw_' + [guid]::NewGuid().ToString('N') + '.txt')
    [IO.File]::WriteAllText($pw, $Cfg['sigre.bd.owner-clave'])
    try {
        & $InitDb -D $Datos -U $Cfg['sigre.bd.owner'] --pwfile=$pw -E UTF8 --locale=C `
            --locale-provider=builtin --builtin-locale=C.UTF-8 `
            --auth-local=scram-sha-256 --auth-host=scram-sha-256 | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'initdb falló' }
    } finally { Remove-Item $pw -Force -ErrorAction SilentlyContinue }

    Add-Content (Join-Path $Datos 'postgresql.conf') @"

# ---- SIGRE Global ----
port = $Puerto
listen_addresses = 'localhost'
timezone = 'America/La_Paz'
log_timezone = 'America/La_Paz'
max_connections = 60
shared_buffers = 128MB
"@
}

function Iniciar {
    if (-not (Test-Path (Join-Path $Datos 'PG_VERSION'))) { Inicializar; $nueva = $true }
    if (-not (Esta-Activa)) {
        Escribir "Iniciando PostgreSQL ($($Bin.Directory.Parent.Name)) en el puerto $Puerto"
        # Se lanza como proceso aparte: el servidor hereda las salidas y, si se capturaran,
        # PowerShell quedaría esperando indefinidamente.
        $salida = Join-Path $env:TEMP 'sigre_pgctl.txt'
        # Ventana oculta propia: el servidor no depende de la consola desde la que se inició.
        $p = Start-Process -FilePath $PgCtl -ArgumentList @('-D', "`"$Datos`"", '-l', "`"$Log`"", '-w', '-t', '60', 'start') `
             -WindowStyle Hidden -PassThru
        $p.WaitForExit()
        if (-not (Esta-Activa)) { throw "No se pudo iniciar PostgreSQL. Revise $Log" }
    } else { Escribir 'PostgreSQL ya estaba en ejecución' 'Green' }

    # Roles y bases de datos (idempotente)
    $app = $Cfg['sigre.bd.app']; $appPw = $Cfg['sigre.bd.app-clave']
    $ro  = $Cfg['sigre.bd.lectura']; $roPw = $Cfg['sigre.bd.lectura-clave']
    Psql-Owner 'postgres' "DO `$`$ BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$app') THEN CREATE ROLE $app LOGIN PASSWORD '$appPw'; END IF;
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$ro')  THEN CREATE ROLE $ro  LOGIN PASSWORD '$roPw';  END IF;
      END `$`$;" | Out-Null
    foreach ($db in @($Cfg['sigre.bd.nombre'], $Cfg['sigre.bd.nombre-pruebas'])) {
        $existe = Psql-Owner 'postgres' "SELECT 1 FROM pg_database WHERE datname = '$db'"
        if (-not $existe) {
            Escribir "Creando la base de datos $db"
            Psql-Owner 'postgres' "CREATE DATABASE $db OWNER $($Cfg['sigre.bd.owner']) ENCODING 'UTF8'" | Out-Null
            Psql-Owner 'postgres' "ALTER DATABASE $db SET timezone TO 'America/La_Paz'" | Out-Null
        }
    }
    Escribir "Base de datos lista: localhost:$Puerto/$($Cfg['sigre.bd.nombre'])" 'Green'
}

function Detener {
    if (Esta-Activa) { & $PgCtl -D $Datos -w stop -m fast | Out-Null; Escribir 'PostgreSQL detenido' 'Green' }
    else { Escribir 'PostgreSQL no estaba en ejecución' 'Yellow' }
}

switch ($Accion) {
    'iniciar' { Iniciar }
    'detener' { Detener }
    'estado'  { if (Esta-Activa) { Escribir "Activa en el puerto $Puerto ($Datos)" 'Green' } else { Escribir 'Detenida' 'Yellow' } }
    'reiniciar-datos' {
        Iniciar
        Escribir 'Borrando y recreando la base de datos (los datos de demostración se cargan al iniciar la aplicación)' 'Yellow'
        Psql-Owner 'postgres' "DROP DATABASE IF EXISTS $($Cfg['sigre.bd.nombre']) WITH (FORCE)" | Out-Null
        $Evidencias = Join-Path $env:LOCALAPPDATA 'SIGRE-Global\evidencias'
        if (Test-Path $Evidencias) { Remove-Item -Recurse -Force $Evidencias }
        Iniciar
    }
    'respaldar' {
        # RNF12: respaldo completo en formato personalizado de pg_dump (se restaura con pg_restore)
        Iniciar
        $Carpeta = Join-Path $env:LOCALAPPDATA 'SIGRE-Global\respaldos'
        New-Item -ItemType Directory -Force $Carpeta | Out-Null
        $Archivo = Join-Path $Carpeta ("sigre_" + (Get-Date -Format 'yyyyMMdd_HHmmss') + '.dump')
        $env:PGPASSWORD = $Cfg['sigre.bd.owner-clave']
        & (Join-Path $BinDir 'pg_dump.exe') -h localhost -p $Puerto -U $Cfg['sigre.bd.owner'] -Fc -f $Archivo $Cfg['sigre.bd.nombre']
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo generar el respaldo' }
        # Conserva los 14 respaldos más recientes
        Get-ChildItem $Carpeta -Filter 'sigre_*.dump' | Sort-Object LastWriteTime -Descending | Select-Object -Skip 14 | Remove-Item -Force
        Escribir "Respaldo creado: $Archivo ($([math]::Round((Get-Item $Archivo).Length / 1KB)) KB)" 'Green'
        Escribir "Para restaurar: pg_restore -h localhost -p $Puerto -U $($Cfg['sigre.bd.owner']) -d sigre --clean `"$Archivo`""
    }
    'consola' {
        Iniciar
        $env:PGPASSWORD = $Cfg['sigre.bd.owner-clave']
        & $Psql -h localhost -p $Puerto -U $Cfg['sigre.bd.owner'] -d $Cfg['sigre.bd.nombre']
    }
}

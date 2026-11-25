# Devuelve la ruta de Java 21 o superior (JAVA_HOME, instalación estándar o PATH).
$candidatos = @()
if ($env:JAVA_HOME) { $candidatos += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
$candidatos += Get-ChildItem 'C:\Program Files\Java\jdk-2*\bin\java.exe', 'C:\Program Files\Eclipse Adoptium\jdk-2*\bin\java.exe' -ErrorAction SilentlyContinue |
    Sort-Object FullName -Descending | ForEach-Object FullName
$enPath = Get-Command java -ErrorAction SilentlyContinue
if ($enPath) { $candidatos += $enPath.Source }
foreach ($c in $candidatos) { if ($c -and (Test-Path $c)) { $c; exit 0 } }
Write-Error 'No se encontró Java 21. Instálelo desde https://adoptium.net'
exit 1

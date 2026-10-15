$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    New-Item -ItemType Directory -Force -Path 'core/build/manual' | Out-Null
    $sources = @(Get-ChildItem 'core/src/main/java','core/src/test/java' -Recurse -Filter '*.java' | ForEach-Object FullName)
    & javac -encoding UTF-8 --release 21 -d core/build/manual @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
    & java -cp core/build/manual dev.lumen.teleportlab.core.SimulationTest
    if ($LASTEXITCODE -ne 0) { throw 'Simulations failed' }
    & python tools/validate_csv.py
    if ($LASTEXITCODE -ne 0) { throw 'CSV validation failed' }
} finally { Pop-Location }

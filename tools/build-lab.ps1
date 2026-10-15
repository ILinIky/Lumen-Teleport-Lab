param([switch]$Install, [switch]$NoInstall, [switch]$Offline)
$ErrorActionPreference = 'Stop'
if ($Install -and $NoInstall) { throw 'Choose either -Install or -NoInstall' }
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & "$PSScriptRoot\build-restricted.ps1" -Offline:$Offline
    $artifact = Join-Path $root 'forge\build\libs\Lumen-Teleport-Lab-1.21.7-1.0.0.jar'
    if (!(Test-Path -LiteralPath $artifact)) { throw 'Expected analyzer JAR not found' }
    Write-Host "Build erfolgreich: $artifact"
    if ($Install) {
        $mods = Join-Path $env:APPDATA '.minecraft\mods'
        New-Item -ItemType Directory -Force -Path $mods | Out-Null
        Copy-Item -LiteralPath $artifact -Destination (Join-Path $mods (Split-Path -Leaf $artifact)) -Force
        Write-Host 'Lab installiert. Minecraft neu starten. F8 oeffnet das Dashboard.'
    }
} finally { Pop-Location }

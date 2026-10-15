param([switch]$Install, [switch]$NoInstall, [switch]$Offline,
      [string]$ModsDirectory = (Join-Path $env:APPDATA '.minecraft\mods'))
$ErrorActionPreference = 'Stop'
if ($Install -and $NoInstall) { throw 'Choose either -Install or -NoInstall' }
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    & "$PSScriptRoot\build-restricted.ps1" -Offline:$Offline
    $manifestPath = Join-Path $root 'forge\build\install-artifact.json'
    if (!(Test-Path -LiteralPath $manifestPath)) { throw 'Build-Artefaktverzeichnis fehlt.' }
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    $artifact = [IO.Path]::GetFullPath((Join-Path $root $manifest.artifact))
    $artifactDirectory = [IO.Path]::GetFullPath((Join-Path $root 'forge\build\libs'))
    if ([IO.Path]::GetDirectoryName($artifact) -ne $artifactDirectory -or
            [IO.Path]::GetFileName($artifact) -notlike 'Lumen-Teleport-Lab-*.jar' -or
            !(Test-Path -LiteralPath $artifact -PathType Leaf)) { throw 'Erwartete aktuelle Lab-JAR fehlt.' }
    Write-Host "Build erfolgreich: $artifact"
    if (!$NoInstall) {
        . "$PSScriptRoot\install-lab.ps1"
        Install-LabArtifact -Artifact $artifact -ModsDirectory $ModsDirectory
        Write-Host 'Aktuelles Teleport Lab installiert. Minecraft neu starten; F8 oeffnet das Dashboard.'
    } else {
        Write-Host 'Nur gebaut (-NoInstall); keine Installation.'
    }
} finally { Pop-Location }

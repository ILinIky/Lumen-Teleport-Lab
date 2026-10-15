function Install-LabArtifact {
    [CmdletBinding()]
    param([Parameter(Mandatory=$true)][string]$Artifact,
          [Parameter(Mandatory=$true)][string]$ModsDirectory)

    $source = Get-Item -LiteralPath $Artifact -ErrorAction Stop
    if ($source.Name -notlike 'Lumen-Teleport-Lab-*.jar') { throw 'Keine Teleport-Lab-JAR ausgewaehlt.' }
    $mods = [IO.Path]::GetFullPath($ModsDirectory)
    New-Item -ItemType Directory -Force -Path $mods -ErrorAction Stop | Out-Null
    $destination = Join-Path $mods $source.Name
    $staging = Join-Path $mods ('.teleportlab-' + [Guid]::NewGuid().ToString('N') + '.tmp')
    $backup = Join-Path (Split-Path -Parent $mods) ('mod-backups\teleportlab\' +
            [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff') + '-' + [Guid]::NewGuid().ToString('N'))
    $moved = @()
    try {
        Copy-Item -LiteralPath $source.FullName -Destination $staging -ErrorAction Stop
        if ((Get-FileHash -LiteralPath $source.FullName -Algorithm SHA256).Hash -ne
                (Get-FileHash -LiteralPath $staging -Algorithm SHA256).Hash) {
            throw 'Pruefsumme der neuen JAR stimmt nicht.'
        }
        $previous = @(Get-ChildItem -LiteralPath $mods -File -Filter 'Lumen-Teleport-Lab-*.jar')
        if ($previous.Count -gt 0) {
            New-Item -ItemType Directory -Force -Path $backup -ErrorAction Stop | Out-Null
            foreach ($old in $previous) {
                # Literal files only; other mods, including Lumen Client, are untouched.
                Move-Item -LiteralPath $old.FullName -Destination (Join-Path $backup $old.Name) -ErrorAction Stop
                $moved += $old.Name
            }
        }
        Move-Item -LiteralPath $staging -Destination $destination -ErrorAction Stop
    } catch {
        $installFailure = $_
        foreach ($name in $moved) {
            Move-Item -LiteralPath (Join-Path $backup $name) -Destination (Join-Path $mods $name) -ErrorAction Continue
        }
        throw "Installation fehlgeschlagen. Minecraft schliessen und erneut starten. $($installFailure.Exception.Message)"
    } finally {
        if (Test-Path -LiteralPath $staging) { Remove-Item -LiteralPath $staging -ErrorAction SilentlyContinue }
    }
    Write-Host "Installiert: $destination"
    if ($moved.Count -gt 0) { Write-Host "Vorherige Lab-Versionen gesichert: $backup" }
}

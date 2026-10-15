# Optional Windows build path for environments blocking Gradle daemon/worker loopback.
param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$savedJavaOpts = $env:JAVA_OPTS
Push-Location $root
try {
    $jvmOptions = '-Xmx3G -Xms64m -Dfile.encoding=UTF-8 -Duser.country=DE -Duser.language=de -Duser.variant'
    $env:JAVA_OPTS = $jvmOptions
    $buildArguments = @('build', '--no-daemon', '-PcommandLineJavac', "-Dorg.gradle.jvmargs=$jvmOptions", '-Dorg.gradle.internal.instrumentation.agent=false')
    if ($Offline) { $buildArguments += '--offline' }
    & .\gradlew.bat @buildArguments
    if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }
} finally { $env:JAVA_OPTS = $savedJavaOpts; Pop-Location }

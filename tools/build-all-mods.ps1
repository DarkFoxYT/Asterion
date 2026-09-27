param([switch] $Offline)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradleArgs = @('--no-daemon')
if ($Offline) { $gradleArgs += '--offline' }

function Find-Jdk([int] $major) {
    $candidates = @()
    $candidates += @(Get-ChildItem 'C:/Program Files/Eclipse Adoptium' -Directory -Filter "jdk-$major*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    $candidates += @(Get-ChildItem 'C:/Program Files/Java' -Directory -Filter "jdk-$major*" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    $candidates = @($candidates | Where-Object { $_ -and (Test-Path (Join-Path $_ 'bin/java.exe')) })
    if ($candidates.Count -eq 0) { throw "JDK $major is required to build this matrix." }
    return $candidates[0]
}

function Invoke-Build([string] $directory, [string[]] $targets, [int] $javaVersion) {
    Push-Location $directory
    $previousJavaHome = $env:JAVA_HOME
    try {
        $env:JAVA_HOME = Find-Jdk $javaVersion
        & .\gradlew.bat @gradleArgs @targets
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed in $directory (exit $LASTEXITCODE)" }
    } finally {
        $env:JAVA_HOME = $previousJavaHome
        Pop-Location
    }
}

Invoke-Build $repo @(
    ':26.1.2-fabric:build', ':26.1.2-quilt:build',
    ':26.1.2-forge:build', ':26.1.2-neoforge:build'
 ) 25
$v15 = Join-Path $repo 'ports/1.5'
Invoke-Build $v15 @(
    ':1.20.1:build', ':1.21.1:build',
    ':1.20.1-forge:build', ':1.21.1-forge:build',
    ':1.20.1-neoforge:build', ':neoforge:build'
 ) 21
Invoke-Build (Join-Path $v15 'ports/26.1.2') @(
    ':26.1.2-fabric:build', ':26.1.2-quilt:build',
    ':26.1.2-forge:build', ':26.1.2-neoforge:build'
 ) 25
& (Join-Path $PSScriptRoot 'verify-modbuilds.ps1')
Write-Output "Asterion jars: $(Join-Path $repo 'modbuilds')"

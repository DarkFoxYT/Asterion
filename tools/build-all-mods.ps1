param(
    [switch] $Offline,
    [switch] $Native,
    [ValidateSet('all', '1.5', '2.0.0')][string] $Release = 'all'
)

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

if ($Release -in @('all', '2.0.0')) {
$v20Targets = @(
    ':26.1.2-fabric:build', ':26.1.2-quilt:build',
    ':26.1.2-forge:build', ':26.1.2-neoforge:build'
 )
if ($Native) {
    $v20Targets += @('-PasterionCompatibilityPorts=true',
        ':26.2-fabric:build', ':26.2-quilt:build',
        ':26.3-fabric:build', ':26.3-quilt:build')
}
Invoke-Build $repo $v20Targets 25
& python (Join-Path $PSScriptRoot 'verify-amnetic-update.py')
if ($LASTEXITCODE -ne 0) { throw 'Asterion 2.0 embedded renderer validation failed.' }
if ($Native) {
    & python (Join-Path $PSScriptRoot 'verify-amnetic-update.py') --native
    if ($LASTEXITCODE -ne 0) { throw 'Asterion 2.0 native renderer validation failed.' }
}
}
if ($Release -in @('all', '1.5')) {
& python (Join-Path $PSScriptRoot 'patch-amnetic-preset-safety.py')
if ($LASTEXITCODE -ne 0) { throw 'Amnetic local-preset safety patch failed.' }
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
& python (Join-Path $PSScriptRoot 'verify-v15-release.py')
if ($LASTEXITCODE -ne 0) { throw 'Asterion 1.5 package validation failed.' }
}
& (Join-Path $PSScriptRoot 'verify-modbuilds.ps1') -Release $Release
Write-Output "Asterion jars: $(Join-Path $repo 'modbuilds')"

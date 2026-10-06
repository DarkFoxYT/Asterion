param(
    [ValidateSet('all', '1.5', '2.0.0')][string] $Release = '1.5',
    [ValidateRange(1, 16)][int] $StartAt = 1
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$output = Join-Path $repo 'build/client-tour'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$mutex = [Threading.Mutex]::new($false, 'Local\AsterionClientTour')
if (-not $mutex.WaitOne(0)) { throw 'An Asterion client tour is already running.' }

function Save-Status($state) {
    $state | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $output 'status.json') -Encoding utf8
}

try {
    & (Join-Path $PSScriptRoot 'verify-modbuilds.ps1') -Release $Release
    $queue = @()
    $modern = Join-Path $repo 'ports/1.5/ports/26.1.2'
    $legacy = Join-Path $repo 'ports/1.5'
    if ($Release -in @('all', '2.0.0')) {
        foreach ($loader in @('fabric', 'quilt', 'forge', 'neoforge')) {
            $queue += @{ name = "2.0.0-$loader-mc26.1.2"; directory = $repo; task = ":26.1.2-${loader}:runClient" }
        }
    }
    if ($Release -in @('all', '1.5')) {
        foreach ($version in @('1.20.1', '1.21.1')) {
            $neo = if ($version -eq '1.20.1') { ':1.20.1-neoforge:runClient' } else { ':neoforge:runClient' }
            $queue += @{ name = "1.5-fabric-mc$version"; directory = $legacy; task = ":${version}:runClient" }
            $queue += @{ name = "1.5-quilt-mc$version"; directory = $legacy; task = ":${version}:runQuiltClient" }
            $queue += @{ name = "1.5-forge-mc$version"; directory = $legacy; task = ":${version}-forge:runClient" }
            $queue += @{ name = "1.5-neoforge-mc$version"; directory = $legacy; task = $neo }
        }
        foreach ($loader in @('fabric', 'quilt', 'forge', 'neoforge')) {
            $queue += @{ name = "1.5-$loader-mc26.1.2"; directory = $modern; task = ":26.1.2-${loader}:runClient" }
        }
    }
    $env:JAVA_HOME = 'C:/Program Files/JetBrains/IntelliJ IDEA 2026.1/jbr'
    if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        throw 'The configured Java runtime is missing.'
    }
    $results = @()
    $statusFile = Join-Path $output 'status.json'
    if ($StartAt -gt 1 -and (Test-Path -LiteralPath $statusFile)) {
        $previous = Get-Content -LiteralPath $statusFile -Raw | ConvertFrom-Json
        $results = @($previous.results | Select-Object -First ($StartAt - 1))
    }
    for ($index = $StartAt - 1; $index -lt $queue.Count; $index++) {
        $entry = $queue[$index]
        $log = Join-Path $output ($entry.name + '.log')
        Save-Status @{ state = 'launching-or-playing'; current = $entry.name; index = $index + 1; total = $queue.Count; log = $log; results = $results; controllerPid = $PID }
        Push-Location $entry.directory
        try {
            # runClient stays alive until its game exits. Never start two games together.
            & .\gradlew.bat $entry.task --console=plain *> $log
            $code = $LASTEXITCODE
        } finally { Pop-Location }
        $results += @{ name = $entry.name; exitCode = $code; log = $log }
        if ($code -ne 0) {
            Save-Status @{ state = 'failed'; current = $entry.name; index = $index + 1; total = $queue.Count; log = $log; results = $results; controllerPid = $PID }
            throw "Client launch failed: $($entry.name). See $log. The queue stopped so the error can be fixed."
        }
    }
    Save-Status @{ state = 'complete'; total = $queue.Count; results = $results; controllerPid = $PID }
} finally {
    $mutex.ReleaseMutex()
    $mutex.Dispose()
}

param(
    [string] $AmneticCheckout = (Join-Path $PSScriptRoot '../../Amnetic'),
    [string] $PatchVersion = '1.0.0-asterion.4',
    [string[]] $NativeMinecraft = @(),
    [switch] $NativeOnly
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$checkout = (Resolve-Path $AmneticCheckout).Path
if ($NativeOnly -and $NativeMinecraft.Count -eq 0) { $NativeMinecraft = @('26.2', '26.3') }
if ($NativeMinecraft.Count -gt 0) {
    $nativeArtifacts = @()
    foreach ($minecraft in $NativeMinecraft) {
        if ($minecraft -notin @('26.2', '26.3')) { throw "Unsupported native target: $minecraft" }
        $upstream = Join-Path $checkout "versions/$minecraft-fabric/build/libs/amnetic-1.0-SNAPSHOT+$minecraft.jar"
        if (!(Test-Path -LiteralPath $upstream)) { throw "Build Amnetic first: missing $upstream" }
        $destination = Join-Path $repo "libs/amnetic-$PatchVersion+$minecraft.jar"
        Copy-Item -LiteralPath $upstream -Destination $destination
        $nativeArtifacts += [ordered]@{ minecraft = $minecraft; loaders = @('fabric', 'quilt');
            file = "libs/amnetic-$PatchVersion+$minecraft.jar";
            upstreamSha256 = (Get-FileHash -LiteralPath $upstream -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
    Push-Location $repo
    try {
        $nativeJars = @($nativeArtifacts | ForEach-Object { Join-Path $repo $_.file })
        & python tools/patch-amnetic-preset-safety.py --jars @nativeJars
        if ($LASTEXITCODE -ne 0) { throw 'Native Amnetic preset compatibility patch failed.' }
        foreach ($artifact in $nativeArtifacts) {
            $artifact.sha256 = (Get-FileHash -LiteralPath (Join-Path $repo $artifact.file) -Algorithm SHA256).Hash.ToLowerInvariant()
        }
        [ordered]@{ sourceCheckout = $checkout; sourceCommit = (& git -C $checkout rev-parse HEAD);
            sourceHasLocalChanges = @(& git -C $checkout status --porcelain).Count -gt 0;
            version = $PatchVersion; artifacts = $nativeArtifacts } |
            ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 'libs/amnetic-native-build.json'
    } finally { Pop-Location }
}
if ($NativeOnly) { return }
$inputs = [ordered]@{
    'amnetic' = Join-Path $checkout 'versions/26.1.2-fabric/build/libs/amnetic-1.0-SNAPSHOT+26.1.2.jar'
    'amnetic-neoforge' = Join-Path $checkout 'neoforge/build/libs/amnetic-neoforge-1.0-SNAPSHOT+26.1.2-neoforge.jar'
    'amnetic-forge' = Join-Path $checkout 'versions/26.1.2-forge/build/libs/amnetic-forge-1.0-SNAPSHOT+26.1.2-forge.jar'
}
foreach ($inputJar in $inputs.Values) {
    if (!(Test-Path -LiteralPath $inputJar)) { throw "Build Amnetic first: missing $inputJar" }
}

Push-Location $repo
try {
    $gradleArgs = @('--no-daemon', '--init-script', 'tools/amnetic-window-compat.gradle', "-Pamnetic_patch_version=$PatchVersion")
    foreach ($artifact in $inputs.Keys) { $gradleArgs += "-P${artifact}_input=$($inputs[$artifact])" }
    & ./gradlew.bat @gradleArgs ':26.1.2-fabric:rebuildAmneticWindowCompatibility'
    if ($LASTEXITCODE -ne 0) { throw 'Amnetic window compatibility patch failed.' }
    $patchedJars = @($inputs.Keys | ForEach-Object { Join-Path $repo "libs/$_-$PatchVersion.jar" })
    & python tools/patch-amnetic-preset-safety.py --jars @patchedJars
    if ($LASTEXITCODE -ne 0) { throw 'Amnetic preset compatibility patch failed.' }

    $sourceCommit = & git -C $checkout rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify Amnetic source revision.' }
    $sourceChanges = @(& git -C $checkout status --porcelain)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify Amnetic working tree state.' }
    $report = [ordered]@{
        installation = 'Built from the supplied Amnetic checkout. Rebuild using tools/update-amnetic.ps1. Preserves loader bootstrap and nested dependencies; applies composable window hook and local-preset safety fixes.'
        version = $PatchVersion
        upstreamVersion = '1.0-SNAPSHOT+26.1.2'
        sourceCommit = $sourceCommit
        sourceHasLocalChanges = $sourceChanges.Count -gt 0
        minecraft = '26.1.2'
        fabricQuiltSha256 = (Get-FileHash -LiteralPath $patchedJars[0] -Algorithm SHA256).Hash.ToLowerInvariant()
        neoForgeSha256 = (Get-FileHash -LiteralPath $patchedJars[1] -Algorithm SHA256).Hash.ToLowerInvariant()
        forgeSha256 = (Get-FileHash -LiteralPath $patchedJars[2] -Algorithm SHA256).Hash.ToLowerInvariant()
        upstreamSha256 = [ordered]@{}
    }
    foreach ($artifact in $inputs.Keys) {
        $report.upstreamSha256[$artifact] = (Get-FileHash -LiteralPath $inputs[$artifact] -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $report | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8 'libs/amnetic-build.json'
} finally {
    Pop-Location
}

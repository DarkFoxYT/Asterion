param(
    [string] $OutputRoot = 'build/downloads/1.5/loader-labelled',
    [string] $Port26Root = 'ports/26.1.2'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$port26 = if ([System.IO.Path]::IsPathRooted($Port26Root)) {
    $Port26Root
} else {
    Join-Path $repo $Port26Root
}
$output = [System.IO.Path]::GetFullPath((Join-Path $repo $OutputRoot))
$buildRoot = [System.IO.Path]::GetFullPath((Join-Path $repo 'build/downloads/1.5'))
if (-not $output.StartsWith($buildRoot + [System.IO.Path]::DirectorySeparatorChar,
        [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'OutputRoot must stay inside build/downloads/1.5.'
}
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Recurse -Force }
New-Item -ItemType Directory -Path $output | Out-Null

function Copy-Mod([string] $relativeSource, [string] $profile, [string] $targetName = '') {
    $source = if ([System.IO.Path]::IsPathRooted($relativeSource)) {
        $relativeSource
    } else {
        Join-Path $repo $relativeSource
    }
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing build: $source" }
    $mods = Join-Path $output "$profile/mods"
    New-Item -ItemType Directory -Force -Path $mods | Out-Null
    if ([string]::IsNullOrEmpty($targetName)) { $targetName = [System.IO.Path]::GetFileName($source) }
    Copy-Item -LiteralPath $source -Destination (Join-Path $mods $targetName)
}

function Copy-Dependency([string] $relativeSource, [string] $profile) {
    Copy-Mod $relativeSource $profile
}

function Copy-ZipDependency([string] $zipName, [string] $fileName, [string] $profile) {
    $zipPath = Join-Path $buildRoot $zipName
    $archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
    try {
        $entry = $archive.GetEntry("mods/$fileName")
        if ($null -eq $entry) { throw "Missing $fileName in $zipPath" }
        $mods = Join-Path $output "$profile/mods"
        New-Item -ItemType Directory -Force -Path $mods | Out-Null
        $stream = [System.IO.File]::Create((Join-Path $mods $fileName))
        try { $entry.Open().CopyTo($stream) } finally { $stream.Dispose() }
    } finally { $archive.Dispose() }
}

function Copy-CachedGecko([string] $fileName, [string] $profile) {
    $cache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'
    $matches = @(Get-ChildItem -LiteralPath $cache -Recurse -Filter $fileName -File)
    if ($matches.Count -ne 1) { throw "Expected one cached $fileName, found $($matches.Count)" }
    $mods = Join-Path $output "$profile/mods"
    New-Item -ItemType Directory -Force -Path $mods | Out-Null
    Copy-Item -LiteralPath $matches[0].FullName -Destination (Join-Path $mods $fileName)
}

function Has-NestedAmnetic([string] $jarPath) {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        return @($archive.Entries | Where-Object {
            $_.FullName -match '^META-INF/(jars|jarjar)/amnetic.*\.jar$'
        }).Count -gt 0
    } finally { $archive.Dispose() }
}

function Assert-ForgeJar([string] $profile, [string] $minecraftVersion) {
    $jarPath = Join-Path $output "$profile/mods/Asterion-1.5-forge-mc$minecraftVersion.jar"
    $archive = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
    try {
        $metadata = $archive.GetEntry('META-INF/mods.toml')
        if ($null -eq $metadata -or $null -ne $archive.GetEntry('fabric.mod.json')) {
            throw "$jarPath is not a Forge-only Asterion JAR"
        }
        $reader = [System.IO.StreamReader]::new($metadata.Open())
        try { $contents = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($contents -notmatch '(?m)^modLoader="javafml"$' -or
            $contents -notmatch '(?m)^version="1\.5"$' -or
            $contents -notmatch "(?m)^versionRange=`"\[$([regex]::Escape($minecraftVersion))\]`"$") {
            throw "$jarPath has incorrect loader, mod version, or Minecraft version metadata"
        }
    } finally { $archive.Dispose() }
}

Copy-Mod 'versions/1.20.1/build/libs/Asterion-1.5-fabric-mc1.20.1.jar' 'mc1.20.1/fabric-quilt' 'Asterion-1.5-fabric-quilt-mc1.20.1.jar'
Copy-ZipDependency 'Asterion-1.5-mc1.20.1-fabric-quilt-beta.zip' 'fabric-api-0.92.12+1.20.1.jar' 'mc1.20.1/fabric-quilt'
Copy-ZipDependency 'Asterion-1.5-mc1.20.1-fabric-quilt-beta.zip' 'geckolib-fabric-1.20.1-4.8.2.jar' 'mc1.20.1/fabric-quilt'
Copy-Dependency 'libs/amnetic-1.0-SNAPSHOT+1.20.1.jar' 'mc1.20.1/fabric-quilt'

Copy-Mod 'versions/1.20.1-forge/build/libs/Asterion-1.5-forge-mc1.20.1.jar' 'mc1.20.1/forge'
Copy-Dependency 'libs/amnetic-forge-1.0-SNAPSHOT+1.20.1-forge.jar' 'mc1.20.1/forge'
Copy-Dependency 'libs/forgified-fabric-api-1.20.1.jar' 'mc1.20.1/forge'
Copy-CachedGecko 'geckolib-forge-1.20.1-4.8.2.jar' 'mc1.20.1/forge'

Copy-Mod 'versions/1.21.1/build/libs/Asterion-1.5-fabric-mc1.21.1.jar' 'mc1.21.1/fabric-quilt' 'Asterion-1.5-fabric-quilt-mc1.21.1.jar'
Copy-ZipDependency 'Asterion-1.5-mc1.21.1-fabric-quilt-beta.zip' 'fabric-api-0.116.17+1.21.1.jar' 'mc1.21.1/fabric-quilt'
Copy-ZipDependency 'Asterion-1.5-mc1.21.1-fabric-quilt-beta.zip' 'geckolib-fabric-1.21.1-4.9.2.jar' 'mc1.21.1/fabric-quilt'
Copy-Dependency 'libs/amnetic-1.0-SNAPSHOT+1.21.1.jar' 'mc1.21.1/fabric-quilt'

Copy-Mod 'versions/1.21.1-forge/build/libs/Asterion-1.5-forge-mc1.21.1.jar' 'mc1.21.1/forge'
Copy-Dependency 'libs/amnetic-forge-1.0-SNAPSHOT+1.21.1-forge.jar' 'mc1.21.1/forge'
Copy-CachedGecko 'geckolib-forge-1.21.1-4.9.2.jar' 'mc1.21.1/forge'

Copy-Mod 'neoforge/build/libs/Asterion-1.5-neoforge-mc1.21.1.jar' 'mc1.21.1/neoforge'
Copy-ZipDependency 'Asterion-1.5-mc1.21.1-neoforge-beta.zip' 'geckolib-neoforge-1.21.1-4.9.2.jar' 'mc1.21.1/neoforge'

$fabric26 = Join-Path $port26 'versions/26.1.2-fabric/build/libs/Asterion-1.5-fabric-mc26.1.2.jar'
Copy-Mod $fabric26 'mc26.1.2/fabric'
Copy-ZipDependency 'Asterion-1.5-mc26.1.2-fabric-beta.zip' 'fabric-api-0.155.0+26.1.2.jar' 'mc26.1.2/fabric'
Copy-ZipDependency 'Asterion-1.5-mc26.1.2-fabric-beta.zip' 'geckolib-fabric-26.1.2-5.5.2.jar' 'mc26.1.2/fabric'
if (-not (Has-NestedAmnetic $fabric26)) {
    Copy-Dependency (Join-Path $port26 'libs/amnetic-1.0-SNAPSHOT+26.1.2.jar') 'mc26.1.2/fabric'
}

$quilt26 = Join-Path $port26 'versions/26.1.2-quilt/build/libs/Asterion-1.5-quilt-mc26.1.2.jar'
Copy-Mod $quilt26 'mc26.1.2/quilt'
Copy-ZipDependency 'Asterion-1.5-mc26.1.2-quilt-beta.zip' 'fabric-api-0.155.0+26.1.2.jar' 'mc26.1.2/quilt'
Copy-ZipDependency 'Asterion-1.5-mc26.1.2-quilt-beta.zip' 'geckolib-fabric-26.1.2-5.5.2.jar' 'mc26.1.2/quilt'
if (-not (Has-NestedAmnetic $quilt26)) {
    Copy-Dependency (Join-Path $port26 'libs/amnetic-1.0-SNAPSHOT+26.1.2.jar') 'mc26.1.2/quilt'
}

Copy-Mod (Join-Path $port26 'versions/26.1.2-neoforge/build/libs/Asterion-1.5-neoforge-mc26.1.2.jar') 'mc26.1.2/neoforge'
Copy-ZipDependency 'Asterion-1.5-mc26.1.2-neoforge-beta.zip' 'geckolib-neoforge-26.1.2-5.5.2.jar' 'mc26.1.2/neoforge'

Copy-Mod (Join-Path $port26 'versions/26.1.2-forge/build/libs/Asterion-1.5-forge-mc26.1.2.jar') 'mc26.1.2/forge'
$amnetic26Forge = Join-Path $port26 'libs/amnetic-forge-26.1.2.jar'
if (-not (Test-Path -LiteralPath $amnetic26Forge)) {
    $amnetic26Forge = Join-Path $port26 'libs/amnetic-forge-1.0-SNAPSHOT+26.1.2-forge.jar'
}
Copy-Dependency $amnetic26Forge 'mc26.1.2/forge'
Copy-CachedGecko 'geckolib-forge-26.1.2-5.5.2.jar' 'mc26.1.2/forge'

Assert-ForgeJar 'mc1.20.1/forge' '1.20.1'
Assert-ForgeJar 'mc1.21.1/forge' '1.21.1'
Assert-ForgeJar 'mc26.1.2/forge' '26.1.2'

$readme = @'
ASTERION 1.5 - LOADER AND MINECRAFT VERSION PACKAGES

Each folder name identifies Minecraft version and loader. The Asterion JAR name
also identifies Asterion version, loader, and Minecraft version. Install JARs
from only one matching mods folder into a clean profile.

Fabric/Quilt 1.20.1 and 1.21.1 share one Fabric-format jar. Minecraft 26.1.2
has separate Fabric and Quilt jars. NeoForge and Forge jars are distinct.

Runtime status (2026-09-27):
- Minecraft 1.20.1 and 1.21.1 Fabric, Minecraft 1.21.1 NeoForge, and
  Minecraft 26.1.2 Fabric, Quilt, and NeoForge started in development clients
  and loaded Asterion resources. This checks startup, not every mechanic.
- Minecraft 1.21.1 Forge loaded Asterion in a development client. Full gameplay
  and mechanics are not yet verified.
- Minecraft 1.20.1 Forge builds, but a Forgified Fabric API mixin crashes in the
  development client. Connector may also be required for that profile; it is
  not bundled here. Treat this profile as experimental.
- Minecraft 26.1.2 Forge builds, but crashes at startup during content
  registration. Treat this profile as experimental; the JAR is not playable.
- All profiles remain beta builds and need in-game mechanic verification.

These status notes are part of the package so that a correct filename is not
mistaken for proof that a mod works on a loader.
'@
[System.IO.File]::WriteAllText((Join-Path $output 'README.txt'), $readme)
[System.IO.File]::WriteAllText((Join-Path $output 'mc1.20.1/forge/STATUS.txt'),
    "Builds, but the development client crashes in a Forgified Fabric API mixin. This profile is experimental and needs runtime repair. Connector is not bundled here.`n")
[System.IO.File]::WriteAllText((Join-Path $output 'mc1.21.1/forge/STATUS.txt'),
    "Asterion loads in the Forge development client. Full gameplay and mechanics are not yet verified.`n")
[System.IO.File]::WriteAllText((Join-Path $output 'mc26.1.2/forge/STATUS.txt'),
    "Builds, but crashes at startup during content registration. This profile is experimental and is not playable.`n")
$zipPath = Join-Path $buildRoot 'Asterion-1.5-mc1.20.1-1.21.1-26.1.2-all-loaders.zip'
if (Test-Path -LiteralPath $zipPath) { Remove-Item -LiteralPath $zipPath -Force }
[System.IO.Compression.ZipFile]::CreateFromDirectory($output, $zipPath,
    [System.IO.Compression.CompressionLevel]::NoCompression, $false)
Write-Output $zipPath

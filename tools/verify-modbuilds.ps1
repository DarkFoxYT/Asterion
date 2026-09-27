$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$shelf = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..')).Path 'modbuilds'
$jars = @(Get-ChildItem -LiteralPath $shelf -File -Filter 'Asterion-*.jar')
if ($jars.Count -eq 0) { throw "No Asterion jars found in $shelf" }

foreach ($file in $jars) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($file.FullName)
    try {
        $nested = @($zip.Entries | Where-Object {
            $_.FullName -match '^META-INF/(jars|jarjar)/amnetic[^/]*\.jar$'
        })
        if ($nested.Count -ne 1) { throw "$($file.Name): expected one embedded Amnetic jar, found $($nested.Count)" }

        $metadataName = if ($file.Name -match '-(fabric|quilt)-') {
            'fabric.mod.json'
        } else {
            'META-INF/jarjar/metadata.json'
        }
        $entry = $zip.GetEntry($metadataName)
        if ($null -eq $entry) { throw "$($file.Name): missing $metadataName" }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($metadataName -eq 'fabric.mod.json') {
            $paths = @($metadata.jars | ForEach-Object { $_.file })
        } else {
            $paths = @($metadata.jars | ForEach-Object { $_.path })
        }
        if ($nested[0].FullName -notin $paths) {
            throw "$($file.Name): $metadataName does not register $($nested[0].FullName)"
        }
    } finally { $zip.Dispose() }
}
Write-Output "Verified embedded Amnetic in $($jars.Count) Asterion jars: $shelf"

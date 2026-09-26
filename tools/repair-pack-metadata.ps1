param([Parameter(Mandatory=$true)][string]$Path)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$packPath = (Resolve-Path -LiteralPath $Path).Path
$archive = [IO.Compression.ZipFile]::OpenRead($packPath)
try {
    $entry = $archive.GetEntry('pack.mcmeta')
    if ($null -eq $entry) { throw 'The archive has no root pack.mcmeta' }
    $reader = [IO.StreamReader]::new($entry.Open())
    try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
} finally { $archive.Dispose() }
if ($metadata.pack.PSObject.Properties.Name -contains 'min_format') { return }
$formats = $metadata.pack.supported_formats
if ($formats -isnot [Array] -or $formats.Length -ne 2 -or $formats[1] -le 64) {
    throw 'This repair only handles legacy two-number supported_formats extending beyond 64'
}
# Preserve the range already declared by the author; no textures or models are changed.
$metadata.pack | Add-Member -NotePropertyName min_format -NotePropertyValue $formats[0]
$metadata.pack | Add-Member -NotePropertyName max_format -NotePropertyValue $formats[1]
$backup = $packPath + '.before-metadata-fix.bak'
if (Test-Path -LiteralPath $backup) { throw "Backup already exists: $backup" }
Copy-Item -LiteralPath $packPath -Destination $backup
$archive = [IO.Compression.ZipFile]::Open($packPath,[IO.Compression.ZipArchiveMode]::Update)
try {
    $archive.GetEntry('pack.mcmeta').Delete()
    $writer = [IO.StreamWriter]::new($archive.CreateEntry('pack.mcmeta').Open(),[Text.UTF8Encoding]::new($false))
    try { $writer.Write(($metadata | ConvertTo-Json -Depth 32)) } finally { $writer.Dispose() }
} finally { $archive.Dispose() }
Write-Output "Repaired pack metadata. Original backup: $backup"

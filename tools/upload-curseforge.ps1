param(
    [string] $Changelog = (Join-Path $PSScriptRoot '../docs/curseforge/CHANGELOG-1.5.md'),
    [ValidateSet('release', 'beta', 'alpha')][string] $ReleaseType = 'release',
    [switch] $Build,
    [switch] $Upload,
    [switch] $ManualRelease
)

$ErrorActionPreference = 'Stop'
if (-not $env:CURSEFORGE_API_TOKEN) {
    $env:CURSEFORGE_API_TOKEN = [Environment]::GetEnvironmentVariable('CURSEFORGE_API_TOKEN', 'User')
}
if ($Build) {
    & (Join-Path $PSScriptRoot 'build-all-mods.ps1') -Release '1.5'
}
$uploadArgs = @('-u', (Join-Path $PSScriptRoot 'upload-curseforge.py'))
if ($Changelog) { $uploadArgs += @('--changelog', $Changelog) }
if ($ReleaseType) { $uploadArgs += @('--release-type', $ReleaseType) }
if ($Upload) { $uploadArgs += '--upload' }
if ($ManualRelease) { $uploadArgs += '--manual-release' }
& python @uploadArgs
if ($LASTEXITCODE -ne 0) { throw 'CurseForge uploader failed; see the error above.' }

$ErrorActionPreference = 'Stop'
$secret = Read-Host 'Paste your CurseForge author upload token (input is hidden)' -AsSecureString
$credential = [System.Net.NetworkCredential]::new('', $secret)
if ([string]::IsNullOrWhiteSpace($credential.Password)) { throw 'Token must not be empty.' }
[Environment]::SetEnvironmentVariable('CURSEFORGE_API_TOKEN', $credential.Password, 'User')
$credential = $null
$secret.Dispose()
Write-Output 'CurseForge token saved in your Windows user environment. The uploader can now read it.'

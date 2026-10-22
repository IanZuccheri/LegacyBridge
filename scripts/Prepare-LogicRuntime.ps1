# Explicit test-only preparation. Never called by the converter, UI or a public mod.
[CmdletBinding()]
param([switch]$DownloadOfficialSourceInstaller)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$root = Join-Path $project 'build/logic-preserving-v1'
$source = Join-Path $root 'source-server'
$target = Join-Path $root 'target-server'
if ((Test-Path -LiteralPath $source) -or (Test-Path -LiteralPath $target)) { throw 'Expected fresh owned server directories; previous results must be retained.' }
$accepted = Join-Path $project 'build/p6-public/server/eula.txt'
if (-not (Test-Path -LiteralPath $accepted) -or (Get-Content -LiteralPath $accepted -Raw) -notmatch '(?m)^eula=true\s*$') { throw 'Requires an already accepted EULA from the existing isolated validation setup; this script does not accept one.' }
if (-not $DownloadOfficialSourceInstaller) { throw 'Explicit -DownloadOfficialSourceInstaller is required; this preparation downloads official Forge/game dependencies.' }
$null = New-Item -ItemType Directory -Path $source
$null = New-Item -ItemType Directory -Path $target
$installer = Join-Path $source 'forge-1.16.5-36.2.42-installer.jar'
$url = 'https://maven.minecraftforge.net/net/minecraftforge/forge/1.16.5-36.2.42/forge-1.16.5-36.2.42-installer.jar'
Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $installer
$checksum = (Invoke-WebRequest -UseBasicParsing -Uri ($url + '.sha1')).Content
$sha1 = $(if ($checksum -is [byte[]]) { [Text.Encoding]::ASCII.GetString($checksum) } else { [string]$checksum }).Trim()
if ($sha1 -notmatch '^[a-fA-F0-9]{40}$' -or (Get-FileHash -LiteralPath $installer -Algorithm SHA1).Hash -ne $sha1) { throw 'Official installer SHA1 mismatch.' }
Write-Output ('SOURCE_INSTALLER_SHA256=' + (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash.ToLowerInvariant())
Copy-Item -LiteralPath (Join-Path $project 'build/p6-public/server/libraries') -Destination (Join-Path $target 'libraries') -Recurse
foreach ($server in @($source, $target)) {
    Copy-Item -LiteralPath $accepted -Destination (Join-Path $server 'eula.txt')
    foreach ($child in @('mods','empty-user-home','tmp')) { $null = New-Item -ItemType Directory -Path (Join-Path $server $child) }
}
Write-Output 'Prepared fresh directories and target installation libraries. Run official source installer separately and serially. No game was started.'

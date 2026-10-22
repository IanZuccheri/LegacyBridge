# Local-only QA preparation. Never launches games/downloads or accepts an EULA.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9][a-z0-9-]{0,31}$')][string]$RunLabel,
    [switch]$SingleClient
)
$ErrorActionPreference = 'Stop'
$ValidationRun = 'p6-public'
. (Join-Path $PSScriptRoot 'ProductionClient.Common.ps1')
. (Join-Path $PSScriptRoot 'NetworkRuntime.Installation.ps1')
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$profileRoot = Join-Path $project 'build/network-snapshot-v1'
$run = Join-Path $profileRoot $RunLabel
$clientBase = Join-Path $project 'build/p6-public/client'
$serverBase = Join-Path $project 'build/logic-preserving-v1/target-server'
$inventory = [Collections.Generic.List[object]]::new()

function Assert-Plain([string]$Path) {
    $entry = Get-Item -LiteralPath $Path -Force
    while ($null -ne $entry) {
        if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'QA paths must not contain aliases, symlinks or junctions.' }
        $entry = if ($entry -is [IO.DirectoryInfo]) { $entry.Parent } else { $entry.Directory }
    }
}
function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Child([string]$Root, [string]$Relative) {
    if ([IO.Path]::IsPathRooted($Relative) -or $Relative -match '(^|[/\\])\.\.([/\\]|$)|:') { throw 'Unsafe installation-relative path.' }
    $path = [IO.Path]::GetFullPath((Join-Path $Root $Relative))
    if (-not $path.StartsWith($Root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Installation path escapes its owned root.' }
    return $path
}
function Ensure-OwnedDirectory([string]$Path) {
    if (Test-Path -LiteralPath $Path) { Assert-Plain $Path; return }
    if (-not $Path.StartsWith($run + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Directory creation escaped fresh run.' }
    Ensure-OwnedDirectory ([IO.Path]::GetDirectoryName($Path))
    $null = New-Item -ItemType Directory -Path $Path
    Assert-Plain $Path
}
function Write-New([string]$Path, [string]$Text) {
    Assert-Plain ([IO.Path]::GetDirectoryName($Path))
    $file = [IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try { $bytes=[Text.UTF8Encoding]::new($false).GetBytes($Text);$file.Write($bytes,0,$bytes.Length);$file.Flush($true) }
    finally { $file.Dispose() }
}
function Copy-Verified([string]$From,[string]$To,[string]$Sha1='') {
    Assert-Plain $From
    if ((Get-Item -LiteralPath $From).PSIsContainer) { throw 'Expected regular installation input.' }
    if ($Sha1) { Assert-ProductionHash $From $Sha1 }
    $before=Hash $From
    Ensure-OwnedDirectory ([IO.Path]::GetDirectoryName($To))
    if (Test-Path -LiteralPath $To) {
        Assert-Plain $To
        if ((Hash $To) -ne $before) { throw 'Conflicting duplicate installation file.' }
        return
    }
    $inputStream=[IO.FileStream]::new($From,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
    try {
        $outputStream=[IO.FileStream]::new($To,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        # Regenerable game libraries/assets: close and rehash every copy below.
        # Force durability for the final receipt, not a disk barrier per asset.
        try {$inputStream.CopyTo($outputStream)}finally{$outputStream.Dispose()}
    } finally {$inputStream.Dispose()}
    if ((Hash $From) -ne $before -or (Hash $To) -ne $before) { throw 'Installation input changed during copy.' }
    $inventory.Add([ordered]@{path=$To.Substring($run.Length+1).Replace('\','/');sha256=$before;size=(Get-Item -LiteralPath $To).Length})
}
function Copy-PlainTree([string]$From,[string]$To) {
    Assert-Plain $From
    Ensure-OwnedDirectory $To
    foreach($entry in @(Get-ChildItem -LiteralPath $From -Force | Sort-Object Name)) {
        Assert-Plain $entry.FullName
        if($entry.PSIsContainer){Copy-PlainTree $entry.FullName (Join-Path $To $entry.Name)}
        else{Copy-Verified $entry.FullName (Join-Path $To $entry.Name)}
    }
}

foreach($path in @($project,(Join-Path $project 'build'),$clientBase,$serverBase)){Assert-Plain $path}
if(Test-Path -LiteralPath $run){throw 'Existing or partial run preserved: choose a fresh RunLabel.'}
foreach($process in @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")){
    if($process.CommandLine -and $process.CommandLine.IndexOf($project,[StringComparison]::OrdinalIgnoreCase) -ge 0){throw 'Finish owned Java/build operations before preparing this run.'}
}
$preparationPath=Join-Path $clientBase 'preparation.json'
Assert-Plain $preparationPath
if((Hash $preparationPath) -ne '1eed6c7fcff6e81ae082b3014c46dcd3f5205658de84db56c0aeaa64145c3fb0'){throw 'P6 installation receipt differs from the audited exact baseline; review explicitly.'}
$prepared=Get-Content -LiteralPath $preparationPath -Raw | ConvertFrom-Json
if($prepared.schemaVersion -ne 2 -or $prepared.minecraft -ne '1.20.1' -or $prepared.forge -ne '47.4.23' -or @($prepared.processorOutputs).Count -ne 3){throw 'Not the audited ordinary Forge installation.'}
$installer=Join-Path $project 'build/p3-production/setup/forge-1.20.1-47.4.23-installer.jar'
$discoveryLibraries=@(Read-NetworkInstallerLibraries $installer)
$forgeRelative='versions/1.20.1-forge-47.4.23/1.20.1-forge-47.4.23.json'
$vanillaRelative='versions/1.20.1/1.20.1.json'
$forgePath=Child $clientBase $forgeRelative
$vanillaPath=Child $clientBase $vanillaRelative
Assert-Plain $forgePath
Assert-Plain $vanillaPath
if((Hash $forgePath) -ne $prepared.forgeMetadataSha256){throw 'Forge metadata hash changed.'}
Assert-ProductionHash $vanillaPath $prepared.vanillaMetadataSha1
$forge=Get-Content -LiteralPath $forgePath -Raw | ConvertFrom-Json
$vanilla=Get-Content -LiteralPath $vanillaPath -Raw | ConvertFrom-Json
if($forge.mainClass -ne 'cpw.mods.bootstraplauncher.BootstrapLauncher' -or $forge.inheritsFrom -ne '1.20.1' -or $forge.id -ne '1.20.1-forge-47.4.23'){throw 'Not the exact ordinary Forge client.'}
$indexRelative='assets/indexes/5.json'
$indexPath=Child $clientBase $indexRelative
Assert-Plain $indexPath
Assert-ProductionHash $indexPath $prepared.assetIndexSha1
$index=Get-Content -LiteralPath $indexPath -Raw | ConvertFrom-Json
$assetHashes=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($asset in $index.objects.PSObject.Properties){
    if($asset.Value.hash -notmatch '^[0-9a-f]{40}$' -or $asset.Value.size -lt 0 -or $asset.Value.size -gt 33554432){throw 'Invalid bounded official asset entry.'}
    $null=$assetHashes.Add([string]$asset.Value.hash)
}
if($assetHashes.Count -ne 3575){throw 'Exact audited asset inventory differs.'}
$accepted=Join-Path $serverBase 'eula.txt'
Assert-Plain $accepted
if((Get-Content -LiteralPath $accepted -Raw) -notmatch '(?m)^eula=true\s*$'){throw 'An already accepted test EULA is required; this script does not accept it.'}
if(-not(Test-Path -LiteralPath $profileRoot)){$null=New-Item -ItemType Directory -Path $profileRoot}
Assert-Plain $profileRoot
$null=New-Item -ItemType Directory -Path $run
Assert-Plain $run
$controlRoles=if($SingleClient){@('server','a')}else{@('server','a','b')}
$installationRoles=if($SingleClient){@('server','client-a')}else{@('server','client-a','client-b')}
foreach($role in $controlRoles){Ensure-OwnedDirectory (Join-Path $run "control/$role")}
foreach($role in $installationRoles){
    $directory=Join-Path $run $role
    Ensure-OwnedDirectory $directory
    foreach($folder in @('mods','empty-user-home','empty-gradle-home','tmp','natives')){Ensure-OwnedDirectory (Join-Path $directory $folder)}
    if($role -eq 'server'){
        Copy-PlainTree (Join-Path $serverBase 'libraries') (Join-Path $directory 'libraries')
        Copy-Verified $accepted (Join-Path $directory 'eula.txt')
    }else{
        Copy-Verified $forgePath (Child $directory $forgeRelative)
        Copy-Verified $vanillaPath (Child $directory $vanillaRelative) $prepared.vanillaMetadataSha1
        Copy-Verified (Child $clientBase 'versions/1.20.1/1.20.1.jar') (Child $directory 'versions/1.20.1/1.20.1.jar') $prepared.vanillaClientSha1
        Copy-Verified $indexPath (Child $directory $indexRelative) $prepared.assetIndexSha1
        foreach($library in (Get-ProductionLibraries $vanilla $forge)){
            $relative='libraries/'+$library.downloads.artifact.path
            Copy-Verified (Child $clientBase $relative) (Child $directory $relative) $library.downloads.artifact.sha1
        }
        # MinecraftLocator/FML language discovery reads these from libraryDirectory,
        # not from the normal bootstrap classpath list in version.json.
        foreach($library in $discoveryLibraries){
            $relative='libraries/'+$library.path
            $source=Child $clientBase $relative
            Assert-Plain $source
            if((Get-Item -LiteralPath $source).Length -ne $library.size){throw 'Discovery library size mismatch.'}
            Copy-Verified $source (Child $directory $relative) $library.sha1
        }
        foreach($output in @($prepared.processorOutputs)){
            $source=Child $clientBase $output.path
            Assert-Plain $source
            if((Hash $source) -ne $output.sha256){throw 'Forge installer output hash changed.'}
            Copy-Verified $source (Child $directory $output.path)
        }
        foreach($hash in @($assetHashes | Sort-Object)){
            $relative='assets/objects/'+$hash.Substring(0,2)+'/'+$hash
            Copy-Verified (Child $clientBase $relative) (Child $directory $relative) $hash
        }
    }
}
$receipt=[ordered]@{
    schemaVersion=1;profile='network-snapshot-v1';runLabel=$RunLabel;operation='PREPARED_LOCAL_ORDINARY_INSTALLATIONS'
    minecraft='1.20.1';forge='47.4.23';clientBaselinePreparationSha256=(Hash $preparationPath)
    clientCount=$(if($SingleClient){1}else{2})
    installerSha256=(Hash $installer);discoveryLibraries=$discoveryLibraries
    files=@($inventory.ToArray());previousWorldsCopied=$false;previousConfigCopied=$false;previousModsCopied=$false
    launcherAccountsRead=$false;downloadsRequested=$false;gameStarted=$false
}
Write-New (Join-Path $run 'preparation-receipt.json') (($receipt|ConvertTo-Json -Depth 8)+[Environment]::NewLine)
Write-Output "NETWORK_RUNTIME_PREPARED runLabel=$RunLabel files=$($inventory.Count) clients=$($receipt.clientCount) servers=1"
Write-Output 'No build, game, download, account read or publication performed. Runtime must be explicitly invoked separately.'

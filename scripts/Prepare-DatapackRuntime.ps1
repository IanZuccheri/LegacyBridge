# Test-only local preparation. No downloads, game launch, config migration, or EULA acceptance.
[CmdletBinding()]
param(
    [ValidatePattern('^$|^[a-z0-9][a-z0-9-]{0,31}$')][string]$RunLabel = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Variant = 'Default' # This profile has one native recipe/reload scenario set; RunLabel creates a new pair.
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$root = Join-Path $project 'build/datapack-recipes-v1'
$variantName = $Variant.ToLowerInvariant()
if ($RunLabel) { $variantName += '-' + $RunLabel }

function Assert-PlainExistingPath([string]$Path) {
    $entry = Get-Item -LiteralPath $Path -Force
    while ($null -ne $entry) {
        if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            throw 'Runtime input/output paths must not contain symlinks or junctions.'
        }
        $entry = if ($entry -is [IO.DirectoryInfo]) { $entry.Parent } else { $entry.Directory }
    }
}
function Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function New-OwnedDirectory([string]$Path) {
    if (Test-Path -LiteralPath $Path) { throw 'Refusing an existing preparation destination; preserve earlier evidence.' }
    Assert-PlainExistingPath ([IO.Path]::GetDirectoryName($Path))
    $null = New-Item -ItemType Directory -Path $Path
    Assert-PlainExistingPath $Path
}
function Write-NewText([string]$Path, [string]$Text) {
    Assert-PlainExistingPath ([IO.Path]::GetDirectoryName($Path))
    $stream = [IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    } finally { $stream.Dispose() }
}
function Copy-NewFile([string]$From, [string]$To, [string]$Server, $Inventory) {
    Assert-PlainExistingPath $From
    Assert-PlainExistingPath ([IO.Path]::GetDirectoryName($To))
    if (-not (Get-Item -LiteralPath $From -Force).PSIsContainer) {
        $before = Hash $From
        $inputStream = [IO.FileStream]::new($From, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
        try {
            $outputStream = [IO.FileStream]::new($To, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
            try { $inputStream.CopyTo($outputStream); $outputStream.Flush($true) } finally { $outputStream.Dispose() }
        } finally { $inputStream.Dispose() }
        if ((Hash $To) -ne $before -or (Hash $From) -ne $before) { throw 'Copied input changed or its hash differs.' }
        $null = $Inventory.Add([ordered]@{
            path = $To.Substring($Server.Length + 1).Replace('\','/')
            sha256 = $before
            size = (Get-Item -LiteralPath $To).Length
        })
    } else { throw 'Expected a regular runtime input file.' }
}
function Copy-LibraryTree([string]$From, [string]$To, [string]$Server, $Inventory) {
    Assert-PlainExistingPath $From
    New-OwnedDirectory $To
    # Enumerate each level only: inspect reparse points before following any directory.
    foreach ($child in @(Get-ChildItem -LiteralPath $From -Force | Sort-Object Name)) {
        if (($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Library tree contains an unsafe alias.' }
        $destination = Join-Path $To $child.Name
        if ($child.PSIsContainer) { Copy-LibraryTree $child.FullName $destination $Server $Inventory }
        else { Copy-NewFile $child.FullName $destination $Server $Inventory }
    }
}

Assert-PlainExistingPath $project
Assert-PlainExistingPath (Join-Path $project 'build')
$sourceBase = Join-Path $project 'build/logic-preserving-v1/source-server'
$targetBase = Join-Path $project 'build/logic-preserving-v1/target-server'
foreach ($base in @($sourceBase, $targetBase)) {
    Assert-PlainExistingPath $base
    Assert-PlainExistingPath (Join-Path $base 'libraries')
    $accepted = Join-Path $base 'eula.txt'
    Assert-PlainExistingPath $accepted
    if ((Get-Content -LiteralPath $accepted -Raw) -notmatch '(?m)^eula=true\s*$') {
        throw 'Requires an already accepted isolated-test EULA; this script does not accept one.'
    }
}
foreach ($sourceFile in @('forge-1.16.5-36.2.42.jar','minecraft_server.1.16.5.jar')) {
    Assert-PlainExistingPath (Join-Path $sourceBase $sourceFile)
}
Assert-PlainExistingPath (Join-Path $targetBase 'libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt')
foreach ($side in @('source','target')) {
    if (Test-Path -LiteralPath (Join-Path $root "$side-$variantName-server")) {
        throw 'This variant already has a prepared or partial directory; retain it and do not overwrite.'
    }
}
foreach ($process in @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")) {
    if ($process.CommandLine -and $process.CommandLine.IndexOf($project,[StringComparison]::OrdinalIgnoreCase) -ge 0) {
        throw 'An owned Java process is active; prepare/build/game operations must be serial.'
    }
}
if (-not (Test-Path -LiteralPath $root)) { New-OwnedDirectory $root }
Assert-PlainExistingPath $root

foreach ($side in @('source','target')) {
    $server = Join-Path $root "$side-$variantName-server"
    $base = if ($side -eq 'source') { $sourceBase } else { $targetBase }
    New-OwnedDirectory $server
    $inventory = [Collections.Generic.List[object]]::new()
    Copy-LibraryTree (Join-Path $base 'libraries') (Join-Path $server 'libraries') $server $inventory
    if ($side -eq 'source') {
        foreach ($name in @('forge-1.16.5-36.2.42.jar','minecraft_server.1.16.5.jar')) {
            Copy-NewFile (Join-Path $base $name) (Join-Path $server $name) $server $inventory
        }
    }
    Copy-NewFile (Join-Path $base 'eula.txt') (Join-Path $server 'eula.txt') $server $inventory
    foreach ($child in @('mods','empty-user-home','empty-gradle-home','tmp')) {
        New-OwnedDirectory (Join-Path $server $child)
    }
    $receipt = [ordered]@{
        schemaVersion = 1
        operation = 'PREPARED_LOCAL_LIBRARIES_ONLY'
        side = $side
        variant = $Variant
        profile = 'datapack-recipes-v1'
        runLabel = $RunLabel
        baseline = if ($side -eq 'source') { '1.16.5-forge-36.2.42' } else { '1.20.1-forge-47.4.23' }
        files = @($inventory.ToArray())
        previousWorldsCopied = $false
        previousConfigCopied = $false
        networkRequested = $false
        gameStarted = $false
    }
    Write-NewText (Join-Path $server 'preparation-receipt.json') (($receipt | ConvertTo-Json -Depth 8) + "`n")
    Write-Output ("PREPARED_DATAPACK_RUNTIME side=$side variant=$Variant")
}
Write-Output 'Preparation complete. No server/build/download ran; prior worlds, logs, configs, mods and receipts remain untouched.'

# Runtime QA only: Source executes the authorized original; Target executes the validated converted mod.
# This runner is never called by conversion, the player UI, or a public LegacyBridge artifact.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Source','Target')][string]$Side,
    [ValidatePattern('^$|^[a-z0-9][a-z0-9-]{0,31}$')][string]$RunLabel = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Variant = 'Default' # One finite native recipe/reload scenario, fresh pair per RunLabel.
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$root = Join-Path $project 'build/datapack-recipes-v1'
$variantName = $Variant.ToLowerInvariant()
if ($RunLabel) { $variantName += '-' + $RunLabel }
$server = Join-Path $root ($Side.ToLowerInvariant() + '-' + $variantName + '-server')

function Assert-PlainExistingPath([string]$Path) {
    $entry = Get-Item -LiteralPath $Path -Force
    while ($null -ne $entry) {
        if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Test paths must not contain symlinks or junctions.' }
        $entry = if ($entry -is [IO.DirectoryInfo]) { $entry.Parent } else { $entry.Directory }
    }
}
function Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function New-ReceiptWriter([string]$Path) {
    Assert-PlainExistingPath ([IO.Path]::GetDirectoryName($Path))
    return [IO.StreamWriter]::new([IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read),[Text.UTF8Encoding]::new($false))
}
function Write-NewText([string]$Path, [string]$Text) {
    Assert-PlainExistingPath ([IO.Path]::GetDirectoryName($Path))
    $stream = [IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try { $bytes=[Text.UTF8Encoding]::new($false).GetBytes($Text); $stream.Write($bytes,0,$bytes.Length); $stream.Flush($true) }
    finally { $stream.Dispose() }
}
function Resolve-PreparedFile([string]$RelativePath) {
    if ([IO.Path]::IsPathRooted($RelativePath) -or $RelativePath -match '(^|[/\\])\.\.([/\\]|$)|:') { throw 'Unsafe preparation receipt path.' }
    $resolved = [IO.Path]::GetFullPath((Join-Path $server $RelativePath))
    if (-not $resolved.StartsWith($server + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Preparation receipt escapes its owned server.' }
    Assert-PlainExistingPath $resolved
    return $resolved
}
function Copy-NewMod([string]$From, [string]$To) {
    Assert-PlainExistingPath $From
    Assert-PlainExistingPath ([IO.Path]::GetDirectoryName($To))
    $before = Hash $From
    $inputStream = [IO.FileStream]::new($From,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
    try {
        $outputStream = [IO.FileStream]::new($To,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try { $inputStream.CopyTo($outputStream); $outputStream.Flush($true) } finally { $outputStream.Dispose() }
    } finally { $inputStream.Dispose() }
    if ((Hash $From) -ne $before -or (Hash $To) -ne $before) { throw 'Runtime artifact changed during copy.' }
    return $before
}

function Assert-ClosedForgeArguments([string]$Text) {
    if ($Text.Length -gt 65536) { throw 'Forge argument file exceeds the finite test limit.' }
    $fixed = @(
        '--add-modules ALL-MODULE-PATH',
        '--add-opens java.base/java.util.jar=cpw.mods.securejarhandler',
        '--add-opens java.base/java.lang.invoke=cpw.mods.securejarhandler',
        '--add-exports java.base/sun.security.util=cpw.mods.securejarhandler',
        '--add-exports jdk.naming.dns/com.sun.jndi.dns=java.naming',
        '-Djava.net.preferIPv6Addresses=system',
        '-DlibraryDirectory=libraries',
        'cpw.mods.bootstraplauncher.BootstrapLauncher',
        '--launchTarget forgeserver',
        '--fml.forgeVersion 47.4.23',
        '--fml.mcVersion 1.20.1',
        '--fml.forgeGroup net.minecraftforge',
        '--fml.mcpVersion 20230612.114412'
    )
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($rawLine in ($Text -split "`n")) {
        $line = $rawLine.Trim()
        if (-not $line) { continue }
        if ($fixed -contains $line) {
            if (-not $seen.Add($line)) { throw 'Duplicate closed Forge argument.' }
            continue
        }
        if ($line.StartsWith('-p ',[StringComparison]::Ordinal)) {
            $key='modulePath'; $paths=$line.Substring(3) -split ';'
        } elseif ($line.StartsWith('-DlegacyClassPath=',[StringComparison]::Ordinal)) {
            $key='legacyPath'; $paths=$line.Substring('-DlegacyClassPath='.Length) -split ';'
        } elseif ($line.StartsWith('-DignoreList=',[StringComparison]::Ordinal)) {
            if (-not $seen.Add('ignoreList')) { throw 'Duplicate Forge ignore list.' }
            foreach ($name in ($line.Substring('-DignoreList='.Length) -split ',')) {
                if ($name -notmatch '^[A-Za-z0-9_.+-]+\.jar$') { throw 'Unapproved Forge ignore-list token.' }
            }
            continue
        } else { throw 'Argument is outside the exact ordinary Forge server allowlist; do not persist or forward launcher credentials.' }
        if (-not $seen.Add($key)) { throw 'Duplicate Forge library path argument.' }
        if (@($paths).Count -lt 1 -or @($paths).Count -gt 256) { throw 'Invalid finite Forge classpath length.' }
        foreach ($relative in $paths) {
            if ($relative -notmatch '^libraries/[A-Za-z0-9_./+-]+\.jar$' -or $relative.Contains('..')) {
                throw 'Forge library token is not a safe local relative JAR.'
            }
            $null = Resolve-PreparedFile $relative
        }
    }
    foreach ($required in @($fixed) + @('modulePath','legacyPath','ignoreList')) {
        if (-not $seen.Contains($required)) { throw 'Ordinary Forge server argument file is incomplete.' }
    }
}

foreach ($path in @($project,$root,$server)) { Assert-PlainExistingPath $path }
foreach ($folder in @('mods','empty-user-home','empty-gradle-home','tmp')) {
    $path = Join-Path $server $folder
    Assert-PlainExistingPath $path
    if (@(Get-ChildItem -LiteralPath $path -Force).Count -ne 0) { throw 'Expected fresh empty mod/home/Gradle/temp directories.' }
}
$allowedInitial = @('libraries','mods','empty-user-home','empty-gradle-home','tmp','eula.txt','preparation-receipt.json')
if ($Side -eq 'Source') { $allowedInitial += @('forge-1.16.5-36.2.42.jar','minecraft_server.1.16.5.jar') }
foreach ($entry in @(Get-ChildItem -LiteralPath $server -Force)) {
    if ($entry.Name -notin $allowedInitial) { throw 'Unexpected file, prior log, trace, or world: use a fresh prepared server.' }
    Assert-PlainExistingPath $entry.FullName
}
$preparationPath = Join-Path $server 'preparation-receipt.json'
Assert-PlainExistingPath $preparationPath
if ((Get-Item -LiteralPath $preparationPath).Length -gt 4194304) { throw 'Preparation receipt exceeds limit.' }
$prepared = Get-Content -LiteralPath $preparationPath -Raw | ConvertFrom-Json
if ($prepared.schemaVersion -ne 1 -or $prepared.operation -ne 'PREPARED_LOCAL_LIBRARIES_ONLY' -or
    $prepared.side -ne $Side.ToLowerInvariant() -or $prepared.variant -ne $Variant -or
    $prepared.profile -ne 'datapack-recipes-v1' -or $prepared.runLabel -ne $RunLabel) { throw 'Preparation receipt does not match the requested scenario.' }
if (@($prepared.files).Count -lt 2 -or @($prepared.files).Count -gt 4096) { throw 'Invalid prepared library inventory size.' }
foreach ($file in @($prepared.files)) {
    $path = Resolve-PreparedFile $file.path
    if ($file.sha256 -notmatch '^[0-9a-f]{64}$' -or (Hash $path) -ne $file.sha256 -or (Get-Item -LiteralPath $path).Length -ne $file.size) {
        throw 'Prepared library, launcher, or EULA was modified after preparation.'
    }
}
if ((Get-Content -LiteralPath (Join-Path $server 'eula.txt') -Raw) -notmatch '(?m)^eula=true\s*$') { throw 'EULA has not already been accepted.' }
foreach ($ownedProcess in @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")) {
    if ($ownedProcess.CommandLine -and $ownedProcess.CommandLine.IndexOf($project,[StringComparison]::OrdinalIgnoreCase) -ge 0) {
        throw 'An owned Java process is active; builds/games must run serially.'
    }
}
$original = Join-Path $project 'fixtures/datapack-recipes-1.16.5/build/libs/legacybridge-datapack-recipes-1.16.5-1.0.0.jar'
Assert-PlainExistingPath $original
$originalHash = Hash $original
$portBase = 25589
if ($Side -eq 'Source') {
    if ([string]::IsNullOrWhiteSpace($env:JAVA8_HOME)) { throw 'Set JAVA8_HOME to your reviewed Java 8 installation before native QA.' }
    $java = Join-Path $env:JAVA8_HOME 'bin/java.exe'
    $version = '1.16.5'; $forge = '36.2.42'; $port = $portBase
    $files = @($original,(Join-Path $project 'fixtures/datapack-runtime-probe-1.16.5/build/libs/legacybridge-datapack-probe-1.16.5-1.0.0.jar'))
    $launch = '-jar forge-1.16.5-36.2.42.jar nogui'
} else {
    $java = 'C:/Program Files/Java/jdk-17/bin/java.exe'
    $version = '1.20.1'; $forge = '47.4.23'; $port = $portBase + 1
    $convertedName = 'converted.jar'
    $files = @((Join-Path $root $convertedName),(Join-Path $project 'legacy-product/build/libs/LegacyBridge.jar'),
        (Join-Path $project 'fixtures/datapack-runtime-probe-1.20.1/build/libs/legacybridge-datapack-probe-1.20.1-1.0.0.jar'))
    $launch = '@libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt nogui'
    $argsPath = Join-Path $server 'libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt'
    Assert-PlainExistingPath $argsPath
    $argsText = Get-Content -LiteralPath $argsPath -Raw
    Assert-ClosedForgeArguments $argsText
}
Assert-PlainExistingPath $java
$hashes = @()
foreach ($file in $files) {
    $destination = Join-Path $server ('mods/' + [IO.Path]::GetFileName($file))
    $hash = Copy-NewMod $file $destination
    $hashes += [ordered]@{fileName=[IO.Path]::GetFileName($file);sha256=$hash;size=(Get-Item -LiteralPath $destination).Length}
}
$propertyLines = @('server-ip=127.0.0.1',"server-port=$port",'online-mode=false','enable-rcon=false','enable-query=false',
    'view-distance=2','simulation-distance=2','spawn-protection=0','level-name=datapack-comparison-world',
    'generate-structures=false','max-players=1')
Write-NewText (Join-Path $server 'server.properties') (($propertyLines -join "`n") + "`n")
$process = [Diagnostics.Process]::new()
$process.StartInfo.FileName = $java
$process.StartInfo.Arguments = '-Xms512M -Xmx2G "-Duser.home=' + (Join-Path $server 'empty-user-home') +
    '" "-Djava.io.tmpdir=' + (Join-Path $server 'tmp') + '" ' + $launch
$process.StartInfo.WorkingDirectory = $server
$process.StartInfo.UseShellExecute = $false
$process.StartInfo.CreateNoWindow = $true
$process.StartInfo.RedirectStandardOutput = $true
$process.StartInfo.RedirectStandardError = $true
$process.StartInfo.RedirectStandardInput = $true
$process.StartInfo.EnvironmentVariables['GRADLE_USER_HOME'] = Join-Path $server 'empty-gradle-home'
foreach ($optionVariable in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')) { $process.StartInfo.EnvironmentVariables.Remove($optionVariable) }
$stdoutPath = Join-Path $server 'behavior-stdout.log'
$stderrPath = Join-Path $server 'behavior-stderr.log'
$stdout = New-ReceiptWriter $stdoutPath
$stderr = New-ReceiptWriter $stderrPath
$success = $false; $stopped = $false; $timedOut = $false; $started = $false; $failureObserved = $false
$total = 0; $watch = [Diagnostics.Stopwatch]::StartNew()
try {
    if (-not $process.Start()) { throw 'Server process did not start.' }
    $started = $true
    $outPending = $process.StandardOutput.ReadLineAsync(); $errPending = $process.StandardError.ReadLineAsync()
    while (-not $process.HasExited -or $null -ne $outPending -or $null -ne $errPending) {
        foreach ($channel in @('out','err')) {
            $pending = if ($channel -eq 'out') { $outPending } else { $errPending }
            if ($null -eq $pending -or -not $pending.IsCompleted) { continue }
            $line = $pending.GetAwaiter().GetResult()
            if ($null -ne $line) {
                $total += $line.Length
                if ($line.Length -gt 65536 -or $total -gt 16777216) { throw 'Console capture exceeded bounded test limits.' }
                $writer = if ($channel -eq 'out') { $stdout } else { $stderr }
                $writer.WriteLine($line); $writer.Flush()
                if ($line -match 'LEGACYBRIDGE_DATAPACK_BEHAVIOR_OK') {
                    if ($success -or $line -notmatch 'LEGACYBRIDGE_DATAPACK_BEHAVIOR_OK.* nativeReloads=2 trace=datapack-trace\.json$') {
                        throw 'Missing, malformed or duplicate native recipe/reload success marker.'
                    }
                    $success=$true; Write-Output "$Side $Variant $line"
                }
                if ($line -match 'LEGACYBRIDGE_DATAPACK_BEHAVIOR_FAILED|Encountered an unexpected exception|Failed to start the minecraft server') { $failureObserved=$true }
                if ($line -match 'All dimensions are saved|All chunks are saved') { $stopped=$true }
                if ($line -match 'Done \(|LEGACYBRIDGE_DATAPACK_BEHAVIOR_FAILED|Stopping server') { Write-Output "$Side $Variant $line" }
                if ($channel -eq 'out') { $outPending=$process.StandardOutput.ReadLineAsync() } else { $errPending=$process.StandardError.ReadLineAsync() }
            } else { if ($channel -eq 'out') { $outPending=$null } else { $errPending=$null } }
        }
        if ($watch.Elapsed.TotalSeconds -gt 240 -and -not $process.HasExited -and -not $timedOut) {
            $timedOut=$true; $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush()
        }
        if ($watch.Elapsed.TotalSeconds -gt 300) { throw 'Owned test server did not stop after timeout; inspect its isolated PID without launching another server.' }
        Start-Sleep -Milliseconds 15
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0 -or -not $success -or -not $stopped -or $timedOut -or $failureObserved) {
        throw "Behavior gate failed: exit=$($process.ExitCode), marker=$success, saved=$stopped, timeout=$timedOut, failure=$failureObserved"
    }
    $trace = Join-Path $server 'datapack-trace.json'
    Assert-PlainExistingPath $trace
    if ((Get-Item -LiteralPath $trace).Length -gt 65536) { throw 'Native trace exceeds limit.' }
    $observation = Get-Content -LiteralPath $trace -Raw | ConvertFrom-Json
    if ($observation.schemaVersion -ne 1 -or $observation.modId -ne 'legacybridge_datapack' -or
        $observation.modVersion -ne '1.0.0' -or $observation.itemStackSize -ne 32 -or
        $observation.ingotRegistryId -ne 'legacybridge_datapack:bridge_ingot' -or
        $observation.blockRegistryId -ne 'legacybridge_datapack:bridge_block' -or
        $observation.nativeReloadCount -ne 2 -or $observation.nativeManagerReplaced -ne $true -or
        $observation.higherPriorityReplaceValidated -ne $true -or $observation.originalPackSelectionRestored -ne $true -or
        $observation.nativeProbeServerThread -ne $true -or $observation.nativeProbePhase -ne 'SERVER_END' -or
        $observation.machineProcessingValidated -ne $false -or $observation.menuInteractionValidated -ne $false) {
        throw 'Native datapack trace identity, actual reload/restore or endpoint scope differs.'
    }
    $expectedRecipes = @{
        Crafting='9,1,0'; Smelting='1,0,1,200,250,3'; Blasting='1,0,1,100,250,3'
        Smoking='1,0,1,100,250,3'; Campfire='1,0,1,600,250,3'; Stonecutting='1,0,2,0,0,3'
    }
    foreach ($phaseName in @('baseline','replacement','restored')) {
        $accepted = if ($phaseName -eq 'replacement') { 'legacybridge_datapack:bridge_block' } else { 'legacybridge_datapack:bridge_ingot' }
        $rejected = if ($phaseName -eq 'replacement') { 'legacybridge_datapack:bridge_ingot' } else { 'legacybridge_datapack:bridge_block' }
        if ($observation.($phaseName+'RecipeCount') -ne 6 -or $observation.($phaseName+'AcceptedItem') -ne $accepted -or
            $observation.($phaseName+'RejectedItem') -ne $rejected) { throw 'Native recipe inventory or tag replacement/restoration differs.' }
        foreach ($recipeName in $expectedRecipes.Keys) {
            if (($observation.($phaseName+$recipeName) -join ',') -ne $expectedRecipes[$recipeName]) {
                throw 'Native recipe matching/assembly trace differs from its independent oracle.'
            }
        }
    }
    $packRoot = Join-Path $server 'datapack-comparison-world/datapacks/legacybridge-tag-replacement'
    Assert-PlainExistingPath $packRoot
    $packFiles = @()
    foreach ($relative in @('pack.mcmeta','data/legacybridge_datapack/tags/items/processing_leaf.json')) {
        $path = Join-Path $packRoot $relative
        Assert-PlainExistingPath $path
        if ((Get-Item -LiteralPath $path).Length -gt 65536) { throw 'Native test datapack file exceeds its finite bound.' }
        $packFiles += [ordered]@{path=$relative;sha256=(Hash $path);size=(Get-Item -LiteralPath $path).Length}
    }
    $packMetadata = Get-Content -LiteralPath (Join-Path $packRoot 'pack.mcmeta') -Raw | ConvertFrom-Json
    $expectedPackFormat = if ($Side -eq 'Source') { 6 } else { 15 }
    if ($packMetadata.pack.pack_format -ne $expectedPackFormat) { throw 'The actual override datapack has an incorrect native format.' }
    $leaf = Get-Content -LiteralPath (Join-Path $packRoot 'data/legacybridge_datapack/tags/items/processing_leaf.json') -Raw | ConvertFrom-Json
    if ($leaf.replace -ne $true -or @($leaf.values).Count -ne 1 -or $leaf.values[0] -ne 'legacybridge_datapack:bridge_block') {
        throw 'The actual higher-priority local replacement pack differs from the native test input.'
    }
    if ((Hash $original) -ne $originalHash) { throw 'The original fixture changed during runtime validation.' }
    for ($index=0; $index -lt $files.Count; $index++) {
        if ((Hash $files[$index]) -ne $hashes[$index].sha256 -or
            (Hash (Join-Path $server ('mods/' + $hashes[$index].fileName))) -ne $hashes[$index].sha256) { throw 'A source or installed runtime artifact changed.' }
    }
    # Close write handles before hashing: Windows readers may reject a concurrent writer handle.
    $stdout.Dispose(); $stderr.Dispose()
    $receipt = [ordered]@{
        schemaVersion=1; side=$Side; variant=$Variant; profile='datapack-recipes-v1'; runLabel=$RunLabel; minecraft=$version; forge=$forge; javaSha256=(Hash $java)
        preparationSha256=(Hash $preparationPath); mods=$hashes; originalFixtureSha256=$originalHash
        originalIntact=$true; traceSha256=(Hash $trace); stdoutSha256=(Hash $stdoutPath); stderrSha256=(Hash $stderrPath)
        exitCode=$process.ExitCode; nativeServerBehavior='PASSED'; nativeProbeThread='SERVER'; nativeProbePhase='SERVER_END'
        nativeRecipeLookupAndAssembly='PASSED'; nativeReloadCount=2; nativeManagerReplaced=$true; higherPriorityReplaceValidated=$true
        originalPackSelectionRestored=$true; testOverridePackFiles=$packFiles; testOverridePackRetainedInactive=$true
        machineProcessing='NOT_RUN'; menuInteraction='NOT_RUN'; recipeBookCategory='NOT_RUN'
        clientVisual='NOT_RUN'; multiplayer='NOT_RUN'; offlineGame='NOT_TESTED'; elapsedSeconds=[int]$watch.Elapsed.TotalSeconds
    }
    Write-NewText (Join-Path $server 'behavior-receipt.json') (($receipt | ConvertTo-Json -Depth 8) + "`n")
    Write-Output ($Side.ToUpperInvariant() + '_' + $Variant.ToUpperInvariant() + '_DATAPACK_RUNTIME_PASSED')
} finally {
    $stdout.Dispose(); $stderr.Dispose()
    if ($started -and -not $process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
    $process.Dispose()
}

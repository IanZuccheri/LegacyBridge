# External console-only QA of the public artifact: no QA mod, Java agent, Gradle, or in-process harness.
# Commands verified against exact 1.20.1/47.4.23 development sources (SHA-256 below), not Bedrock syntax.
# See MinecraftServer.reloadResources, ExecuteCommand, ForceLoadCommand, ItemEntity and ReloadCommand.
# This script does NOT assert recipe assembly or client assets/translations; those require separate GUI evidence.
[CmdletBinding()]
param(
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17',
    [ValidateRange(30, 180)][int]$StartupTimeoutSeconds = 180,
    [switch]$Launch,
    [ValidateSet('p6-public', 'embedded-simple')][string]$ValidationRun = 'p6-public',
    [string]$PublicSha256 = '',
    [string]$ConvertedSha256 = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
# Pure mode selection is separately testable without preparing paths or launching a JVM.
function Get-P6ValidationProfile([string]$Run, [string]$PublicHash, [string]$ConvertedHash) {
    if ($Run -ceq 'p6-public') {
        if ($PublicHash.Length -ne 0 -or $ConvertedHash.Length -ne 0) { throw 'Historical P6 artifact pins cannot be overridden; select a new validation run.' }
        return [pscustomobject]@{
            PublicSha256 = '00cc7ebde1279c1ac3cc6ac0a54498e4598ca4f45700a6799f915ce9dddea991'
            ConvertedSha256 = '541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb'
            EvidencePrefix = 'p6-public-server'; OutputPrefix = 'P6_PUBLIC_SERVER'
            Scenario = 'P6_PUBLIC_ONLY_DEDICATED_SERVER'; LevelNamePattern = '^p6-[a-z0-9-]{1,40}$'
        }
    }
    if ($Run -cne 'embedded-simple') { throw 'Unknown owned dedicated-server validation run.' }
    if ($PublicHash -cnotmatch '\A[0-9a-f]{64}\z' -or $ConvertedHash -cnotmatch '\A[0-9a-f]{64}\z') {
        throw 'embedded-simple requires explicit lowercase -PublicSha256 and -ConvertedSha256 of the reviewed final artifacts.'
    }
    return [pscustomobject]@{
        PublicSha256 = $PublicHash; ConvertedSha256 = $ConvertedHash
        EvidencePrefix = 'embedded-simple-server'; OutputPrefix = 'EMBEDDED_SIMPLE_SERVER'
        Scenario = 'EMBEDDED_SIMPLE_PUBLIC_ONLY_DEDICATED_SERVER'; LevelNamePattern = '^embedded-simple-[a-z0-9-]{1,40}$'
    }
}
$validation = Get-P6ValidationProfile $ValidationRun $PublicSha256 $ConvertedSha256
$expectedPublic = $validation.PublicSha256
$expectedConverted = $validation.ConvertedSha256
function Assert-P6Path([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    $entry = Get-Item -LiteralPath $resolved -Force
    while ($null -ne $entry) {
        if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'P6 server paths must not contain symlinks/junctions/reparse points.' }
        $entry = if ($entry -is [IO.DirectoryInfo]) { $entry.Parent } else { $entry.Directory }
    }
    return (Get-Item -LiteralPath $resolved -Force).FullName.TrimEnd('\')
}
function Get-P6Hash([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function New-P6Writer([string]$Path) {
    return [IO.StreamWriter]::new([IO.FileStream]::new($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read), [Text.UTF8Encoding]::new($false))
}
$project = Assert-P6Path (Join-Path $PSScriptRoot '..')
$server = Assert-P6Path (Join-Path $project "build/$ValidationRun/server")
$modsDirectory = Assert-P6Path (Join-Path $server 'mods')
$modFiles = @(Get-ChildItem -LiteralPath $modsDirectory -Force)
if ($modFiles.Count -ne 2 -or @($modFiles | Where-Object { $_.PSIsContainer -or $_.Extension -ne '.jar' }).Count -ne 0) { throw 'Server mods must contain exactly the public JAR and one applied fixture JAR, with no QA mod.' }
$publicJar = Assert-P6Path (Join-Path $modsDirectory 'LegacyBridge.jar')
$convertedJar = Assert-P6Path (@($modFiles | Where-Object { $_.Name -ne 'LegacyBridge.jar' })[0].FullName)
if ((Get-P6Hash $publicJar) -ne $expectedPublic -or (Get-P6Hash $convertedJar) -ne $expectedConverted) { throw 'Final public or applied baseline artifact hash mismatch.' }
$java = Assert-P6Path (Join-Path $JavaHome 'bin/java.exe')
$javaRelease = Get-Content -LiteralPath (Assert-P6Path (Join-Path $JavaHome 'release')) -Raw
if ($javaRelease -notmatch '(?m)^JAVA_VERSION="(17\.[^"]+)"\s*$') { throw 'This validation is pinned to Java 17.' }
$javaVersion = $Matches[1]
$javaHash = Get-P6Hash $java
$argRelative = 'libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt'
$argFile = Assert-P6Path (Join-Path $server $argRelative)
$argText = Get-Content -LiteralPath $argFile -Raw
if ($argText -notmatch '(?m)^--launchTarget forgeserver\s*$' -or $argText -notmatch '(?m)^--fml.forgeVersion 47\.4\.23\s*$' -or
        $argText -notmatch '(?m)^--fml.mcVersion 1\.20\.1\s*$' -or $argText -match '(?i)\.gradle|userdev|javaagent|legacybridge.product.p[3456]') { throw 'Not the pinned ordinary Forge dedicated-server argument file.' }
# Every runtime library token is relative to this prepared installation; no development cache is consulted.
foreach ($match in [regex]::Matches($argText, 'libraries/[^;\s]+\.jar')) { $null = Assert-P6Path (Join-Path $server $match.Value) }
$properties = @{}
foreach ($line in [IO.File]::ReadAllLines((Assert-P6Path (Join-Path $server 'server.properties')))) {
    if ($line -match '^([^#!][^=]*)=(.*)$') { if ($properties.ContainsKey($Matches[1])) { throw 'Duplicate server property.' }; $properties[$Matches[1]] = $Matches[2] }
}
foreach ($entry in @{ 'server-ip' = '127.0.0.1'; 'server-port' = '25596'; 'enable-rcon' = 'false'; 'enable-query' = 'false'; 'online-mode' = 'false' }.GetEnumerator()) {
    if (-not $properties.ContainsKey($entry.Key) -or $properties[$entry.Key] -ne $entry.Value) { throw "Unsafe or unexpected isolated server property: $($entry.Key)" }
}
if (-not $properties.ContainsKey('level-name') -or $properties['level-name'] -cnotmatch $validation.LevelNamePattern) { throw 'World name must identify a fresh world owned by the selected validation run, without separators.' }
if (Test-Path -LiteralPath (Join-Path $server $properties['level-name'])) { throw 'The selected server world already exists; refusing to overwrite/reuse unreviewed evidence.' }
if ((Get-Content -LiteralPath (Assert-P6Path (Join-Path $server 'eula.txt')) -Raw) -notmatch '(?m)^eula=true\s*$') { throw 'The prepared installation must already have an accepted EULA; this runner does not accept it.' }
$emptyUser = Assert-P6Path (Join-Path $server 'empty-user-home')
$temporary = Assert-P6Path (Join-Path $server 'tmp')
if (@(Get-ChildItem -LiteralPath $emptyUser -Force).Count -ne 0 -or (Test-Path -LiteralPath (Join-Path $server 'legacybridge/cache/mapping-data'))) { throw 'Server verification requires an initially empty user home and no mapping bundle.' }
$paths = @{}
foreach ($kind in @('stdout', 'stderr', 'commands', 'classloading', 'receipt')) {
    $suffix = if ($kind -eq 'receipt') { 'json' } else { 'log' }
    $paths[$kind] = Join-Path $server ($validation.EvidencePrefix + "-$kind.$suffix")
    if (Test-Path -LiteralPath $paths[$kind]) { throw "Existing evidence must be preserved before another run: $kind" }
}
if (Test-Path -LiteralPath (Join-Path $server 'logs/latest.log')) { throw 'Expected a fresh server installation without an existing latest.log.' }
if (-not $Launch) { Write-Output ($validation.OutputPrefix + '_PREFLIGHT_OK — no server launched. Invoke with -Launch only after the client has stopped.'); return }
$clientPath = Join-Path $project "build/$ValidationRun/client"
foreach ($candidate in @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")) {
    if ($candidate.CommandLine -and $candidate.CommandLine.IndexOf($clientPath, [StringComparison]::OrdinalIgnoreCase) -ge 0) { throw 'The selected owned client is still running; server validation must be serial.' }
}

$state = @{ observed = [Text.StringBuilder]::new(); failures = [Collections.Generic.List[string]]::new(); channels = @(); commandWriter = $null }
$process = [Diagnostics.Process]::new()
$processStarted = $false
$forced = $false
$failureMessage = $null
$exitCode = $null
$startedUtc = [DateTime]::UtcNow
$verified = [Collections.Generic.List[string]]::new()
$sourceHash = '4bd6656a7cbe3809da68f3b709ca0f2fa186575f58538668c58ce30cb38c4932'
function Read-P6Output {
    foreach ($channel in $state.channels) {
        while (-not $channel.ended -and $null -ne $channel.pending -and $channel.pending.IsCompleted) {
            $line = $channel.pending.GetAwaiter().GetResult()
            if ($null -eq $line) { $channel.ended = $true; break }
            if ($line.Length -gt 65536 -or $state.observed.Length + $line.Length -gt 8388608) { throw 'Server console exceeded bounded capture limits.' }
            $channel.writer.WriteLine($line); $channel.writer.Flush()
            $null = $state.observed.AppendLine($line)
            if ($line -match '(?i)Unknown or incomplete command|Incorrect argument|Unknown (block|item)|Cannot summon|Unable to summon|No entity was found|No elements matching|Reload failed|Failed to execute reload|Exception executing command|Missing or unsupported mandatory dependencies|Failed to start the minecraft server|Encountered an unexpected exception|Error loading registry data') { $state.failures.Add($line) }
            if ($line -match 'Done \(|\[Server\] P6_|Stopping server|All dimensions are saved') { Write-Output ($validation.OutputPrefix + "_OBSERVED $line") }
            $channel.pending = $channel.reader.ReadLineAsync()
        }
    }
}
function Drain-P6FailedCapture {
    # Only after recording a capture failure: keep OS pipes flowing so a safe stop can finish.
    # Discarded output is not evidence and the receipt remains FAILED.
    foreach ($channel in $state.channels) {
        while (-not $channel.ended -and $null -ne $channel.pending -and $channel.pending.IsCompleted) {
            try {
                $line = $channel.pending.GetAwaiter().GetResult()
                if ($null -eq $line) { $channel.ended = $true; break }
                $channel.pending = $channel.reader.ReadLineAsync()
            } catch { $channel.ended = $true; $state.failures.Add('Console drain failed after capture failure: ' + $_.Exception.Message) }
        }
    }
}
function Send-P6Command([string]$Command) {
    if ($process.HasExited) { throw 'Owned server exited before the next console command.' }
    $state.commandWriter.WriteLine(([DateTime]::UtcNow.ToString('o') + ' ' + $Command)); $state.commandWriter.Flush()
    $process.StandardInput.WriteLine($Command); $process.StandardInput.Flush()
}
function Wait-P6Pattern([string]$Pattern, [int]$Seconds, [int]$Offset = 0) {
    $deadline = [DateTime]::UtcNow.AddSeconds($Seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        Read-P6Output
        if ($state.failures.Count -ne 0) { throw ('Server command/runtime failure: ' + $state.failures[0]) }
        if ($state.observed.ToString().Substring($Offset) -match $Pattern) { return }
        if ($process.HasExited) { throw 'Server exited before the required live evidence appeared.' }
        Start-Sleep -Milliseconds 100
    }
    throw "Timed out waiting for required live evidence: $Pattern"
}
try {
    $stdoutWriter = New-P6Writer $paths.stdout
    $state.channels += @{ writer = $stdoutWriter; reader = $null; pending = $null; ended = $false }
    $stderrWriter = New-P6Writer $paths.stderr
    $state.channels += @{ writer = $stderrWriter; reader = $null; pending = $null; ended = $false }
    $state.commandWriter = New-P6Writer $paths.commands
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $java; $start.WorkingDirectory = $server
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true; $start.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = [Text.UTF8Encoding]::new($false); $start.StandardErrorEncoding = [Text.UTF8Encoding]::new($false)
    foreach ($variable in @('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')) { $start.EnvironmentVariables.Remove($variable) }
    $start.Arguments = '-Xms512m -Xmx2g -Djava.awt.headless=true -Dfile.encoding=UTF-8 -Djavax.net.ssl.trustStore=NONE -Djavax.net.ssl.trustStoreType=Windows-ROOT ' +
        '"-Duser.home=' + $emptyUser + '" "-Djava.io.tmpdir=' + $temporary + '" -Xlog:class+load=info:file=' + $validation.EvidencePrefix + '-classloading.log @' + $argRelative + ' nogui'
    $process.StartInfo = $start
    if (-not $process.Start()) { throw 'Failed to start owned server process.' }
    $processStarted = $true
    $ownedStart = $process.StartTime.ToUniversalTime().Ticks
    Write-Output ($validation.OutputPrefix + "_STARTED pid=$($process.Id) Forge=47.4.23 Minecraft=1.20.1 Java=$javaVersion")
    $state.channels[0].reader = $process.StandardOutput; $state.channels[1].reader = $process.StandardError
    foreach ($channel in $state.channels) { $channel.pending = $channel.reader.ReadLineAsync() }
    Wait-P6Pattern 'Done \([0-9.,]+s\)! For help, type "help"' $StartupTimeoutSeconds
    $verified.Add('ordinary_forge_done')
    Send-P6Command 'forceload add 0 0'
    $chunkDeadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        Send-P6Command 'execute if loaded 0 100 0 run say P6_CHUNK_READY'
        Start-Sleep -Milliseconds 500; Read-P6Output
        if ($state.failures.Count -ne 0) { throw $state.failures[0] }
    } while ($state.observed.ToString() -notmatch '\[Server\] P6_CHUNK_READY\s' -and [DateTime]::UtcNow -lt $chunkDeadline)
    Wait-P6Pattern '\[Server\] P6_CHUNK_READY\s' 2
    Send-P6Command 'setblock 0 100 0 legacybridge_example:bridge_block'
    Send-P6Command 'execute if block 0 100 0 legacybridge_example:bridge_block run say P6_BLOCK_OK'
    Wait-P6Pattern '\[Server\] P6_BLOCK_OK\s' 15; $verified.Add('registered_block_native_placement')
    Send-P6Command 'execute if block 0 100 0 #legacybridge_example:bridge_blocks run say P6_BLOCK_TAG_OK'
    Wait-P6Pattern '\[Server\] P6_BLOCK_TAG_OK\s' 15; $verified.Add('block_tag_native_match')
    Send-P6Command 'summon minecraft:item 0.5 101 0.5 {Item:{id:"legacybridge_example:bridge_ingot",Count:1b},Tags:["p6_probe"],NoGravity:1b,Age:-32768s,PickupDelay:32767s}'
    Send-P6Command 'data get entity @e[type=minecraft:item,tag=p6_probe,limit=1] Item'
    Send-P6Command 'execute if entity @e[type=minecraft:item,tag=p6_probe,nbt={Item:{id:"legacybridge_example:bridge_ingot",Count:1b}},limit=1] run say P6_ITEM_OK'
    Wait-P6Pattern '\[Server\] P6_ITEM_OK\s' 15; $verified.Add('registered_item_native_entity_nbt')
    # reload executes on the server thread; exact MinecraftServer source managedBlock waits for its future.
    # The following marker thus occurs after reload returns, but is NOT recipe assembly/crafting evidence.
    $reloadOffset = $state.observed.Length
    Send-P6Command 'reload'
    Send-P6Command 'execute if block 0 100 0 #legacybridge_example:bridge_blocks run say P6_RELOAD_RETURNED_OK'
    Wait-P6Pattern '\[Server\] P6_RELOAD_RETURNED_OK\s' 45 $reloadOffset
    Wait-P6Pattern 'Loaded [0-9]+ recipes' 5 $reloadOffset
    $verified.Add('datapack_reload_returned_without_detected_errors')
    Send-P6Command 'stop'
    $stopDeadline = [DateTime]::UtcNow.AddSeconds(60)
    while (-not $process.HasExited -and [DateTime]::UtcNow -lt $stopDeadline) { Read-P6Output; Start-Sleep -Milliseconds 100 }
    if (-not $process.HasExited) { throw 'Server did not stop safely within the shutdown deadline.' }
    $process.WaitForExit(); $exitCode = $process.ExitCode
    $drainDeadline = [DateTime]::UtcNow.AddSeconds(5)
    while (@($state.channels | Where-Object { -not $_.ended }).Count -ne 0 -and [DateTime]::UtcNow -lt $drainDeadline) { Read-P6Output; Start-Sleep -Milliseconds 50 }
    if ($exitCode -ne 0 -or $state.failures.Count -ne 0 -or $state.observed.ToString() -notmatch 'All dimensions are saved') { throw 'Normal exit, command success, or world-save evidence is missing.' }
    $verified.Add('normal_stop_and_world_save')
    if ((Get-P6Hash $publicJar) -ne $expectedPublic -or (Get-P6Hash $convertedJar) -ne $expectedConverted) { throw 'Artifact bytes changed during server validation.' }
    $classLoading = Get-Content -LiteralPath (Assert-P6Path $paths.classloading) -Raw
    if ($classLoading -match '(?i)[/\\]\.gradle[/\\]|dev\.legacybridge\.product\.client\.|dev\.legacybridge\.product\.validation\.') { throw 'Development cache, exclusive product client class, or QA harness resolved in dedicated-server JVM.' }
    $verified.Add('no_development_cache_or_product_client_classes_resolved')
} catch {
    $failureMessage = $_.Exception.Message
} finally {
    if ($processStarted -and -not $process.HasExited) {
        try {
            # Safe-stop must not depend on a command log writer that may have run out of disk space.
            $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush()
        } catch { $failureMessage = "$failureMessage; shutdown request failed: $($_.Exception.Message)" }
        $stopDeadline = [DateTime]::UtcNow.AddSeconds(60)
        $discardAfterFailure = $false
        while (-not $process.HasExited -and [DateTime]::UtcNow -lt $stopDeadline) {
            if ($discardAfterFailure) { Drain-P6FailedCapture }
            else {
                try { Read-P6Output }
                catch { $failureMessage = "$failureMessage; shutdown log capture failed: $($_.Exception.Message)"; $discardAfterFailure = $true }
            }
            Start-Sleep -Milliseconds 100
        }
        if (-not $process.HasExited) {
            if ((Get-Process -Id $process.Id).StartTime.ToUniversalTime().Ticks -ne $ownedStart) { throw 'PID ownership changed; refusing forced termination.' }
            $process.Kill(); $forced = $true; $process.WaitForExit(10000) | Out-Null
            $failureMessage = "$failureMessage; owned process exceeded safe-stop deadline and required forced termination"
        }
    }
    if ($processStarted -and $process.HasExited) { $exitCode = $process.ExitCode; try { Read-P6Output } catch { $failureMessage = "$failureMessage; final log drain failed: $($_.Exception.Message)" } }
    foreach ($channel in $state.channels) { $channel.writer.Dispose() }
    if ($null -ne $state.commandWriter) { $state.commandWriter.Dispose() }
    $receipt = [ordered]@{ schemaVersion = 1; scenario = $validation.Scenario; validationRun = $ValidationRun; status = $(if ($null -eq $failureMessage) { 'PASSED' } else { 'FAILED' });
        startedUtc = $startedUtc.ToString('o'); finishedUtc = [DateTime]::UtcNow.ToString('o'); minecraft = '1.20.1'; forge = '47.4.23'; java = $javaVersion;
        javaSha256 = $javaHash; publicSha256 = $expectedPublic; convertedSha256 = $expectedConverted; forgeArgumentsSha256 = (Get-P6Hash $argFile);
        developmentSourceReferenceSha256 = $sourceHash; processId = $(if ($processStarted) { $process.Id } else { $null }); exitCode = $exitCode; forcedTermination = $forced;
        externalStdinOnly = $true; qaMod = $false; mappingBundle = $false; initialUserHomeEmpty = $true; offlineClaim = $false;
        checks = @($verified); failure = $failureMessage; artifacts = @{};
        limitations = @('Reload is not recipe assembly proof. Crafting, assets and translations require separate real client evidence.',
            'No network isolation was configured by this runner; no offline claim follows from online-mode=false.', 'No original mod classes are executed; only the approved converted fixture is loaded.') }
    foreach ($kind in @('stdout', 'stderr', 'commands', 'classloading')) { if (Test-Path -LiteralPath $paths[$kind]) { $receipt.artifacts[$kind] = @{ path = [IO.Path]::GetFileName($paths[$kind]); sha256 = (Get-P6Hash $paths[$kind]) } } }
    $receiptWriter = New-P6Writer $paths.receipt
    try { $receiptWriter.Write(($receipt | ConvertTo-Json -Depth 8)); $receiptWriter.Flush(); $receiptWriter.BaseStream.Flush($true) } finally { $receiptWriter.Dispose() }
    $process.Dispose()
}
if ($null -ne $failureMessage) { throw "$ValidationRun public-only dedicated server FAILED; see $($paths.receipt): $failureMessage" }
Write-Output ($validation.OutputPrefix + "_OK receipt=$($paths.receipt)")

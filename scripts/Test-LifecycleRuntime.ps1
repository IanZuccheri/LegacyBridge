# Runtime QA only: Source executes the authorized original; Target executes the validated converted mod.
# This runner is never called by conversion, the player UI, or a public LegacyBridge artifact.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidateSet('Source','Target')][string]$Side,
    [ValidateSet('Default','Configured','InvalidConfig','Inferred')][string]$Variant = 'Default',
    [ValidatePattern('^$|^[a-z0-9][a-z0-9-]{0,31}$')][string]$RunLabel = ''
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$root = Join-Path $project 'build/lifecycle-preserving-v1'
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

foreach ($path in @($project,$root,$server)) { Assert-PlainExistingPath $path }
foreach ($folder in @('mods','empty-user-home','empty-gradle-home','tmp')) {
    $path = Join-Path $server $folder
    Assert-PlainExistingPath $path
    if (@(Get-ChildItem -LiteralPath $path -Force).Count -ne 0) { throw 'Expected fresh empty mod/home/Gradle/temp directories.' }
}
$allowedInitial = @('libraries','mods','empty-user-home','empty-gradle-home','tmp','eula.txt','preparation-receipt.json')
if ($Side -eq 'Source') { $allowedInitial += @('forge-1.16.5-36.2.42.jar','minecraft_server.1.16.5.jar') }
if ($Variant -in @('Configured','InvalidConfig')) { $allowedInitial += 'config' }
foreach ($entry in @(Get-ChildItem -LiteralPath $server -Force)) {
    if ($entry.Name -notin $allowedInitial) { throw 'Unexpected file, prior log, trace, or world: use a fresh prepared server.' }
    Assert-PlainExistingPath $entry.FullName
}
$preparationPath = Join-Path $server 'preparation-receipt.json'
Assert-PlainExistingPath $preparationPath
if ((Get-Item -LiteralPath $preparationPath).Length -gt 4194304) { throw 'Preparation receipt exceeds limit.' }
$prepared = Get-Content -LiteralPath $preparationPath -Raw | ConvertFrom-Json
$expectedFactor = switch ($Variant) { 'Configured' { 5 } 'InvalidConfig' { 8 } default { 3 } }
if ($prepared.schemaVersion -ne 1 -or $prepared.operation -ne 'PREPARED_LOCAL_LIBRARIES_ONLY' -or
    $prepared.side -ne $Side.ToLowerInvariant() -or $prepared.variant -ne $Variant -or
    $prepared.expectedFactor -ne $expectedFactor -or $prepared.expectedEnabled -ne $true) { throw 'Preparation receipt does not match the requested scenario.' }
if (@($prepared.files).Count -lt 2 -or @($prepared.files).Count -gt 4096) { throw 'Invalid prepared library inventory size.' }
foreach ($file in @($prepared.files)) {
    $path = Resolve-PreparedFile $file.path
    if ($file.sha256 -notmatch '^[0-9a-f]{64}$' -or (Hash $path) -ne $file.sha256 -or (Get-Item -LiteralPath $path).Length -ne $file.size) {
        throw 'Prepared library, launcher, or EULA was modified after preparation.'
    }
}
if ((Get-Content -LiteralPath (Join-Path $server 'eula.txt') -Raw) -notmatch '(?m)^eula=true\s*$') { throw 'EULA has not already been accepted.' }
$config = Join-Path $server 'config/legacybridge_lifecycle-common.toml'
$configInputHash = $null
if ($Variant -in @('Configured','InvalidConfig')) {
    Assert-PlainExistingPath $config
    if (@(Get-ChildItem -LiteralPath (Join-Path $server 'config') -Force).Count -ne 1) { throw 'Only the explicitly prepared COMMON config may exist.' }
    $configInputHash = Hash $config
    if ($configInputHash -ne $prepared.configInputSha256) { throw 'Explicit config input was modified after preparation.' }
}
foreach ($ownedProcess in @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")) {
    if ($ownedProcess.CommandLine -and $ownedProcess.CommandLine.IndexOf($project,[StringComparison]::OrdinalIgnoreCase) -ge 0) {
        throw 'An owned Java process is active; builds/games must run serially.'
    }
}
$original = Join-Path $project 'fixtures/lifecycle-content-1.16.5/build/libs/legacybridge-lifecycle-1.16.5-1.0.0.jar'
if ($Variant -eq 'Inferred') { $original = Join-Path $project 'fixtures/lifecycle-inferred-1.16.5/build/libs/legacybridge-lifecycle-inferred-1.16.5-1.0.0.jar' }
Assert-PlainExistingPath $original
$originalHash = Hash $original
$portBase = switch ($Variant) { 'Configured' { 25593 } 'InvalidConfig' { 25591 } default { 25595 } }
if ($Side -eq 'Source') {
    if ([string]::IsNullOrWhiteSpace($env:JAVA8_HOME)) { throw 'Set JAVA8_HOME to your reviewed Java 8 installation before native QA.' }
    $java = Join-Path $env:JAVA8_HOME 'bin/java.exe'
    $version = '1.16.5'; $forge = '36.2.42'; $port = $portBase
    $files = @($original,(Join-Path $project 'fixtures/lifecycle-runtime-probe-1.16.5/build/libs/legacybridge-lifecycle-probe-1.16.5-1.0.0.jar'))
    $launch = '-jar forge-1.16.5-36.2.42.jar nogui'
} else {
    $java = 'C:/Program Files/Java/jdk-17/bin/java.exe'
    $version = '1.20.1'; $forge = '47.4.23'; $port = $portBase + 1
    $convertedName = if ($Variant -eq 'Inferred') { 'converted-inferred.jar' } else { 'converted.jar' }
    $files = @((Join-Path $root $convertedName),(Join-Path $project 'legacy-product/build/libs/LegacyBridge.jar'),
        (Join-Path $project 'fixtures/lifecycle-runtime-probe-1.20.1/build/libs/legacybridge-lifecycle-probe-1.20.1-1.0.0.jar'))
    $launch = '@libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt nogui'
    $argsPath = Join-Path $server 'libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt'
    Assert-PlainExistingPath $argsPath
    $argsText = Get-Content -LiteralPath $argsPath -Raw
    if ($argsText -match '(?i)\.gradle|userdev|javaagent|accessToken|clientToken|password|--username|[a-z]:[/\\]' -or
        $argsText -notmatch '--launchTarget forgeserver' -or $argsText -notmatch '--fml.forgeVersion 47\.4\.23') {
        throw 'Not the expected ordinary local Forge server argument file; no sensitive launcher arguments are allowed.'
    }
}
Assert-PlainExistingPath $java
$hashes = @()
foreach ($file in $files) {
    $destination = Join-Path $server ('mods/' + [IO.Path]::GetFileName($file))
    $hash = Copy-NewMod $file $destination
    $hashes += [ordered]@{fileName=[IO.Path]::GetFileName($file);sha256=$hash;size=(Get-Item -LiteralPath $destination).Length}
}
$propertyLines = @('server-ip=127.0.0.1',"server-port=$port",'online-mode=false','enable-rcon=false','enable-query=false',
    'view-distance=2','simulation-distance=2','spawn-protection=0','level-name=lifecycle-comparison-world',
    'generate-structures=false','max-players=1')
Write-NewText (Join-Path $server 'server.properties') (($propertyLines -join "`n") + "`n")
$process = [Diagnostics.Process]::new()
$process.StartInfo.FileName = $java
$process.StartInfo.Arguments = '-Xms512M -Xmx2G "-Duser.home=' + (Join-Path $server 'empty-user-home') +
    '" "-Djava.io.tmpdir=' + (Join-Path $server 'tmp') + '" -Dlegacybridge.probe.expectedEnabled=true -Dlegacybridge.probe.expectedFactor=' +
    $expectedFactor + ' ' + $launch
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
                if ($line -match 'LEGACYBRIDGE_LIFECYCLE_BEHAVIOR_OK') { $success=$true; Write-Output "$Side $Variant $line" }
                if ($line -match 'LEGACYBRIDGE_LIFECYCLE_BEHAVIOR_FAILED|Encountered an unexpected exception|Failed to start the minecraft server') { $failureObserved=$true }
                if ($line -match 'All dimensions are saved|All chunks are saved') { $stopped=$true }
                if ($line -match 'Done \(|LEGACYBRIDGE_LIFECYCLE_BEHAVIOR_FAILED|Stopping server') { Write-Output "$Side $Variant $line" }
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
    $trace = Join-Path $server 'lifecycle-trace.json'
    Assert-PlainExistingPath $trace
    if ((Get-Item -LiteralPath $trace).Length -gt 65536) { throw 'Native trace exceeds limit.' }
    $observation = Get-Content -LiteralPath $trace -Raw | ConvertFrom-Json
    if ($observation.schemaVersion -ne 1 -or $observation.modId -ne 'legacybridge_lifecycle' -or
        $observation.configFactor -ne $expectedFactor -or $observation.configLoaded -ne $true -or
        $observation.configEnabled -ne $true -or $observation.nativeProbeServerThread -ne $true) { throw 'Native trace identity or configuration assertion failed.' }
    Assert-PlainExistingPath $config
    if ((Get-Item -LiteralPath $config).Length -gt 1048576) { throw 'Native COMMON config exceeds test limit.' }
    $configText = Get-Content -LiteralPath $config -Raw
    if ($configText -notmatch ('(?m)^\s*factor\s*=\s*' + $expectedFactor + '\s*$') -or
        $configText -notmatch '(?m)^\s*enabled\s*=\s*true\s*$') { throw 'Native persisted config does not contain the observed valid values.' }
    if ((Hash $original) -ne $originalHash) { throw 'The original fixture changed during runtime validation.' }
    for ($index=0; $index -lt $files.Count; $index++) {
        if ((Hash $files[$index]) -ne $hashes[$index].sha256 -or
            (Hash (Join-Path $server ('mods/' + $hashes[$index].fileName))) -ne $hashes[$index].sha256) { throw 'A source or installed runtime artifact changed.' }
    }
    # Close write handles before hashing: Windows readers may reject a concurrent writer handle.
    $stdout.Dispose(); $stderr.Dispose()
    $receipt = [ordered]@{
        schemaVersion=1; side=$Side; variant=$Variant; minecraft=$version; forge=$forge; javaSha256=(Hash $java)
        preparationSha256=(Hash $preparationPath); mods=$hashes; originalFixtureSha256=$originalHash
        originalIntact=$true; traceSha256=(Hash $trace); stdoutSha256=(Hash $stdoutPath); stderrSha256=(Hash $stderrPath)
        configInputSha256=$configInputHash; configOutputSha256=(Hash $config); expectedFactor=$expectedFactor
        nativeConfigCorrection=if($Variant -eq 'InvalidConfig'){'NUMERIC_99_CLAMPED_TO_8'}else{'NOT_REQUESTED'}
        exitCode=$process.ExitCode; nativeServerBehavior='PASSED'; nativeProbeThread='SERVER'; queuedWorkThread='NOT_OBSERVED'
        clientVisual='NOT_RUN'; multiplayer='NOT_RUN'; offlineGame='NOT_TESTED'; elapsedSeconds=[int]$watch.Elapsed.TotalSeconds
    }
    Write-NewText (Join-Path $server 'behavior-receipt.json') (($receipt | ConvertTo-Json -Depth 8) + "`n")
    Write-Output ($Side.ToUpperInvariant() + '_' + $Variant.ToUpperInvariant() + '_LIFECYCLE_RUNTIME_PASSED')
} finally {
    $stdout.Dispose(); $stderr.Dispose()
    if ($started -and -not $process.HasExited) { $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
    $process.Dispose()
}

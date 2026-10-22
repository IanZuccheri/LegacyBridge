# Comparative behavior test ONLY: executes original code in Source and converted code in Target.
# Never invoked by conversion. A native server-side API trace is not visual client/multiplayer QA.
[CmdletBinding()]
param([Parameter(Mandatory=$true)][ValidateSet('Source','Target')][string]$Side)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$root=Join-Path $project 'build/logic-preserving-v1'
$server=Join-Path $root ($Side.ToLowerInvariant()+'-server')
function New-ReceiptWriter([string]$path) {
    return [IO.StreamWriter]::new([IO.FileStream]::new($path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read),[Text.UTF8Encoding]::new($false))
}
function Hash([string]$path) { return (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() }
if (Test-Path -LiteralPath (Join-Path $server 'logic-trace.json')) { throw 'Previous trace must not be overwritten.' }
if (Test-Path -LiteralPath (Join-Path $server 'logs/latest.log')) { throw 'Use a fresh prepared test instance, not a prior world or log.' }
if (Test-Path -LiteralPath (Join-Path $server 'logic-comparison-world')) { throw 'An existing world cannot be reused by this comparison runner.' }
foreach($folder in @($project,$root,$server,(Join-Path $server 'mods'))) {
    $entry=Get-Item -LiteralPath $folder -Force
    while($null -ne $entry) {
        if(($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Test paths must not be symlinks/junctions.' }
        $entry=if($entry -is [IO.DirectoryInfo]){$entry.Parent}else{$entry.Directory}
    }
}
if (@(Get-ChildItem -LiteralPath (Join-Path $server 'mods') -Force).Count -ne 0) { throw 'Expected initially empty isolated mods directory.' }
if ((Get-Content -LiteralPath (Join-Path $server 'eula.txt') -Raw) -notmatch '(?m)^eula=true\s*$') { throw 'EULA has not already been accepted.' }
foreach($process in @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")) {
    if($process.CommandLine -and $process.CommandLine.IndexOf($project,[StringComparison]::OrdinalIgnoreCase) -ge 0) { throw 'An owned Java process is active; builds/games must be serial.' }
}
if($Side -eq 'Source') {
    if ([string]::IsNullOrWhiteSpace($env:JAVA8_HOME)) { throw 'Set JAVA8_HOME to your reviewed Java 8 installation before native QA.' }
    $java = Join-Path $env:JAVA8_HOME 'bin/java.exe'
    $version='1.16.5'; $forge='36.2.42'; $port=25597
    $files=@((Join-Path $project 'fixtures/logic-preservation-1.16.5/build/libs/legacybridge-logic-1.16.5-1.0.0.jar'),(Join-Path $project 'fixtures/logic-runtime-probe-1.16.5/build/libs/legacybridge-logic-probe-1.16.5-1.0.0.jar'))
    $launch='-jar forge-1.16.5-36.2.42.jar nogui'
} else {
    $java='C:/Program Files/Java/jdk-17/bin/java.exe'
    $version='1.20.1'; $forge='47.4.23'; $port=25598
    $files=@((Join-Path $root 'converted.jar'),(Join-Path $project 'legacy-product/build/libs/LegacyBridge.jar'),(Join-Path $project 'fixtures/logic-runtime-probe-1.20.1/build/libs/legacybridge-logic-probe-1.20.1-1.0.0.jar'))
    $launch='@libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt nogui'
    $argsText=Get-Content -LiteralPath (Join-Path $server 'libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt') -Raw
    if($argsText -match '(?i)\.gradle|userdev|javaagent') { throw 'Not an isolated ordinary Forge argument file.' }
}
$hashes=@()
foreach($file in $files) {
    $hashes+=@{fileName=[IO.Path]::GetFileName($file);sha256=(Hash $file)}
    Copy-Item -LiteralPath $file -Destination (Join-Path $server ('mods/'+[IO.Path]::GetFileName($file)))
}
$properties=New-ReceiptWriter (Join-Path $server 'server.properties')
try { foreach($line in @('server-ip=127.0.0.1',"server-port=$port",'online-mode=false','enable-rcon=false','enable-query=false','view-distance=2','simulation-distance=2','spawn-protection=0','level-name=logic-comparison-world','generate-structures=false','max-players=1')) { $properties.WriteLine($line) } } finally { $properties.Dispose() }
$process=[Diagnostics.Process]::new()
$process.StartInfo.FileName=$java
$process.StartInfo.Arguments='-Xms512M -Xmx2G "-Duser.home='+ (Join-Path $server 'empty-user-home') +'" "-Djava.io.tmpdir='+ (Join-Path $server 'tmp') +'" '+$launch
$process.StartInfo.WorkingDirectory=$server
$process.StartInfo.UseShellExecute=$false
$process.StartInfo.CreateNoWindow=$true
$process.StartInfo.RedirectStandardOutput=$true
$process.StartInfo.RedirectStandardError=$true
$process.StartInfo.RedirectStandardInput=$true
$process.StartInfo.EnvironmentVariables['GRADLE_USER_HOME']=Join-Path $server 'empty-user-home/absent-gradle'
$stdout=New-ReceiptWriter (Join-Path $server 'behavior-stdout.log')
$stderr=New-ReceiptWriter (Join-Path $server 'behavior-stderr.log')
$success=$false; $stopped=$false; $timedOut=$false; $started=$false; $failureObserved=$false; $total=0; $watch=[Diagnostics.Stopwatch]::StartNew()
try {
    if(-not $process.Start()) { throw 'Server process did not start.' }
    $started=$true
    $outPending=$process.StandardOutput.ReadLineAsync(); $errPending=$process.StandardError.ReadLineAsync()
    while(-not $process.HasExited -or $null -ne $outPending -or $null -ne $errPending) {
        foreach($channel in @('out','err')) {
            $pending=if($channel -eq 'out'){$outPending}else{$errPending}
            if($null -eq $pending -or -not $pending.IsCompleted) { continue }
            $line=$pending.GetAwaiter().GetResult()
            if($null -ne $line) {
                $total+=$line.Length
                if($line.Length -gt 65536 -or $total -gt 16777216) { throw 'Console capture exceeded bounded test limits.' }
                $writer=if($channel -eq 'out'){$stdout}else{$stderr}; $writer.WriteLine($line); $writer.Flush()
                if($line -match 'LEGACYBRIDGE_LOGIC_BEHAVIOR_OK') {$success=$true;Write-Output "$Side $line"}
                if($line -match 'LEGACYBRIDGE_LOGIC_BEHAVIOR_FAILED|Encountered an unexpected exception|Failed to start the minecraft server') {$failureObserved=$true}
                if($line -match 'All dimensions are saved|All chunks are saved') {$stopped=$true}
                if($line -match 'Done \(|LEGACYBRIDGE_LOGIC_BEHAVIOR_FAILED|Stopping server') {Write-Output "$Side $line"}
                if($channel -eq 'out'){$outPending=$process.StandardOutput.ReadLineAsync()}else{$errPending=$process.StandardError.ReadLineAsync()}
            } else { if($channel -eq 'out'){$outPending=$null}else{$errPending=$null} }
        }
        if($watch.Elapsed.TotalSeconds -gt 240 -and -not $process.HasExited -and -not $timedOut) { $timedOut=$true; $process.StandardInput.WriteLine('stop'); $process.StandardInput.Flush() }
        if($watch.Elapsed.TotalSeconds -gt 300) { throw 'Owned test server did not stop after timeout; inspect the isolated process.' }
        Start-Sleep -Milliseconds 15
    }
    $process.WaitForExit()
    if($process.ExitCode -ne 0 -or -not $success -or -not $stopped -or $timedOut -or $failureObserved) { throw "Behavior gate failed: exit=$($process.ExitCode), marker=$success, saved=$stopped, timeout=$timedOut, failure=$failureObserved" }
    $trace=Join-Path $server 'logic-trace.json'
    $receipt=New-ReceiptWriter (Join-Path $server 'behavior-receipt.json')
    try { $receipt.WriteLine((@{schemaVersion=1;side=$Side;minecraft=$version;forge=$forge;javaSha256=(Hash $java);mods=$hashes;traceSha256=(Hash $trace);exitCode=$process.ExitCode;nativeServerBehavior='PASSED';clientVisual='NOT_RUN';multiplayer='NOT_RUN';elapsedSeconds=[int]$watch.Elapsed.TotalSeconds} | ConvertTo-Json -Depth 6)) } finally { $receipt.Dispose() }
    Write-Output ($Side.ToUpperInvariant()+'_LOGIC_RUNTIME_PASSED')
} finally { $stdout.Dispose();$stderr.Dispose();if($started -and -not $process.HasExited){$process.StandardInput.WriteLine('stop');$process.StandardInput.Flush()}; $process.Dispose() }

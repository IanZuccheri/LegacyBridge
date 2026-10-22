# Explicit NET-QA-02 handshake slice. Two native roles, no converter changes or original execution.
# Launcher manifest expansion and installer inventory reuse the existing audited QA helpers.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9][a-z0-9-]{0,31}$')][string]$RunLabel,
    [Parameter(Mandatory=$true)][ValidateSet('MATCHED','CLIENT_ABSENT','CLIENT_PROTOCOL','SERVER_ABSENT','SERVER_PROTOCOL','PLATFORM_CLIENT_ABSENT')][string]$Scenario,
    [switch]$MojangDebug,
    [switch]$PacketMarkers,
    [switch]$SocketSnapshot,
    [switch]$PacketRecording,
    [switch]$ReadInterestTrace,
    [switch]$ReadClearTrace
)
$ErrorActionPreference='Stop'
$ValidationRun='p6-public'
. (Join-Path $PSScriptRoot 'ProductionClient.Common.ps1')
. (Join-Path $PSScriptRoot 'NetworkRuntime.Installation.ps1')
. (Join-Path $PSScriptRoot 'NetworkHandshake.Diagnostics.ps1')
. (Join-Path $PSScriptRoot 'NetworkHandshake.Control.ps1')
. (Join-Path $PSScriptRoot 'NetworkHandshake.Trace.ps1')
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$profileRoot=Join-Path $project 'build/network-snapshot-v1'
$run=Join-Path $profileRoot $RunLabel
$control=Join-Path $run 'control'
$java='C:/Program Files/Java/jdk-17/bin/java.exe'
$actors=@{}
$runtimeRoles=@('server','a')
$inventory=[Collections.Generic.List[object]]::new()
$globalWatch=[Diagnostics.Stopwatch]::StartNew()
$networkFailure=$false
$caseContract=Get-NetworkHandshakeCase $Scenario
$runMode=Get-NetworkHandshakeRunMode $MojangDebug.IsPresent $PacketMarkers.IsPresent $SocketSnapshot.IsPresent $PacketRecording.IsPresent $caseContract.PlatformBaseline $ReadInterestTrace.IsPresent $ReadClearTrace.IsPresent
$readTraceRequested=$ReadInterestTrace -or $ReadClearTrace
$passed=$false
Write-Output "HANDSHAKE_QA_PREFLIGHT runLabel=$RunLabel scenario=$Scenario mode=$($runMode.Name) gameStarted=false"

function Assert-Plain([string]$Path){
    $entry=Get-Item -LiteralPath $Path -Force
    while($null -ne $entry){
        if($entry.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'QA paths must not contain aliases, symlinks or junctions.'}
        $entry=if($entry -is [IO.DirectoryInfo]){$entry.Parent}else{$entry.Directory}
    }
}
function Hash([string]$Path){(Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Child([string]$Root,[string]$Relative){
    if([IO.Path]::IsPathRooted($Relative) -or $Relative -match '(^|[/\\])\.\.([/\\]|$)|:'){throw 'Unsafe relative receipt path.'}
    $path=[IO.Path]::GetFullPath((Join-Path $Root $Relative))
    if(-not $path.StartsWith($Root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Receipt path escapes its owned run.'}
    return $path
}
function Write-New([string]$Path,[string]$Text){
    Assert-Plain ([IO.Path]::GetDirectoryName($Path))
    $file=[IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try{$bytes=[Text.UTF8Encoding]::new($false).GetBytes($Text);$file.Write($bytes,0,$bytes.Length);$file.Flush($true)}finally{$file.Dispose()}
}
function New-LogWriter([string]$Path){
    Assert-Plain ([IO.Path]::GetDirectoryName($Path))
    return [IO.StreamWriter]::new([IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read),[Text.UTF8Encoding]::new($false))
}
function Copy-Mod([string]$From,[string]$To){
    Assert-Plain $From
    Assert-Plain ([IO.Path]::GetDirectoryName($To))
    $before=Hash $From
    $inputStream=[IO.FileStream]::new($From,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
    try{
        $outputStream=[IO.FileStream]::new($To,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
        try{$inputStream.CopyTo($outputStream);$outputStream.Flush($true)}finally{$outputStream.Dispose()}
    }finally{$inputStream.Dispose()}
    if((Hash $From) -ne $before -or (Hash $To) -ne $before){throw 'Runtime artifact changed during copy.'}
    $inventory.Add([ordered]@{path=$To.Substring($run.Length+1).Replace('\','/');sha256=$before;size=(Get-Item -LiteralPath $To).Length})
}
function Read-ZipText([string]$Path,[string]$Entry){
    $zip=[IO.Compression.ZipFile]::OpenRead($Path)
    try{
        $item=$zip.GetEntry($Entry)
        if($null -eq $item -or $item.Length -gt 65536){throw 'Missing/oversized required artifact metadata.'}
        $reader=[IO.StreamReader]::new($item.Open())
        try{return $reader.ReadToEnd()}finally{$reader.Dispose()}
    }finally{$zip.Dispose()}
}
function Safe-ArgumentFile([string]$Directory,[string[]]$Arguments){
    if(($Arguments -join ' ') -match 'accessToken|clientId|auth_|xuid|--username|--uuid|forgeclientuserdev|forge_gradle|[\\/]\.gradle[\\/]'){
        throw 'Credential/identity arguments or a development classpath cannot be persisted.'
    }
    if($Arguments.Count -gt 256 -or ($Arguments -join ' ').Length -gt 65536){throw 'Argument file exceeds closed test limits.'}
    $lines=@($Arguments|ForEach-Object{'"'+$_.Replace('\','\\').Replace('"','\"')+'"'})
    $path=Join-Path $Directory 'qa-safe-arguments.txt'
    Write-New $path (($lines -join [Environment]::NewLine)+[Environment]::NewLine)
    return $path
}
function Native-OfflineUuid([string]$Name){
    $md5=[Security.Cryptography.MD5]::Create()
    try{$bytes=$md5.ComputeHash([Text.Encoding]::UTF8.GetBytes('OfflinePlayer:'+$Name))}finally{$md5.Dispose()}
    $bytes[6]=($bytes[6] -band 15) -bor 48
    $bytes[8]=($bytes[8] -band 63) -bor 128
    return (($bytes|ForEach-Object{$_.ToString('x2')}) -join '')
}
function Start-Actor([string]$Role){
    if($Role -notin $runtimeRoles){throw 'The requested actor is outside the selected explicit runtime mode.'}
    if($actors.ContainsKey($Role) -or $actors.Count -ge 2){throw 'At most the two explicit handshake roles may be launched.'}
    $folder=if($Role -eq 'server'){'server'}else{'client-'+$Role}
    $directory=Join-Path $run $folder
    $arguments=@('-Xms256m',$(if($Role -eq 'server'){'-Xmx1536m'}else{'-Xmx1792m'}),'-Dfile.encoding=UTF-8',
        '-Djavax.net.ssl.trustStore=NONE','-Djavax.net.ssl.trustStoreType=Windows-ROOT',
        ('-Djava.io.tmpdir='+(Join-Path $directory 'tmp')),('-Duser.home='+(Join-Path $directory 'empty-user-home')),
        ('-Dlegacybridge.netqa.role='+$Role),('-Dlegacybridge.netqa.control='+$control),('-Dlegacybridge.handshake.case='+$Scenario))
    $identityArguments=''
    $arguments+=@($runMode.Arguments)
    if($Role -ceq 'a'){$arguments+=@($runMode.ClientArguments)}
    if($Role -eq 'server'){
        # Java does not recursively expand @files inside another argument file.
        $identityArguments=' @libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt nogui'
    }else{
        $vanilla=Get-Content -LiteralPath (Join-Path $directory 'versions/1.20.1/1.20.1.json') -Raw|ConvertFrom-Json
        $forge=Get-Content -LiteralPath (Join-Path $directory 'versions/1.20.1-forge-47.4.23/1.20.1-forge-47.4.23.json') -Raw|ConvertFrom-Json
        $classpath=[Collections.Generic.List[string]]::new()
        foreach($library in (Get-ProductionLibraries $vanilla $forge)){
            $path=Child (Join-Path $directory 'libraries') $library.downloads.artifact.path
            Assert-Plain $path
            Assert-ProductionHash $path $library.downloads.artifact.sha1
            $classpath.Add($path)
        }
        $name=if($Role -eq 'a'){'LegacyNetA'}else{'LegacyNetB'}
        $variables=@{
            version_name='1.20.1-forge-47.4.23';game_directory=$directory;assets_root=(Join-Path $directory 'assets')
            assets_index_name='5';user_type='legacy';version_type='release';resolution_width='960';resolution_height='540'
            natives_directory=(Join-Path $directory 'natives');launcher_name='LegacyBridgeIsolatedNetworkQA';launcher_version='1'
            classpath=($classpath -join ';');library_directory=(Join-Path $directory 'libraries');classpath_separator=';'
            auth_player_name=$name;auth_uuid=(Native-OfflineUuid $name);auth_access_token='0';clientid='legacybridge-local-qa';auth_xuid='0'
        }
        $arguments+=@(Expand-ProductionArguments $vanilla.arguments.jvm $variables)
        $arguments+=@(Expand-ProductionArguments $forge.arguments.jvm $variables)
        $arguments+=$forge.mainClass
        $gameArguments=@(Expand-ProductionArguments $vanilla.arguments.game $variables)+@(Expand-ProductionArguments $forge.arguments.game $variables)
        $seenIdentity=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
        for($i=0;$i -lt $gameArguments.Count;$i++){
            if($gameArguments[$i] -in @('--username','--uuid','--accessToken','--clientId','--xuid')){
                if(-not $seenIdentity.Add($gameArguments[$i]) -or $i+1 -ge $gameArguments.Count){throw 'Malformed identity argument in exact manifest.'}
                # Only local synthetic identity in process memory. This string is never logged or persisted.
                $identityArguments+=' '+$gameArguments[$i]+' "'+$gameArguments[$i+1]+'"'
                $i++
            }else{$arguments+=$gameArguments[$i]}
        }
        if($seenIdentity.Count -ne 5){throw 'Exact vanilla identity argument inventory changed.'}
    }
    $argumentFile=Safe-ArgumentFile $directory $arguments
    $process=[Diagnostics.Process]::new()
    $process.StartInfo.FileName=$java
    $process.StartInfo.Arguments='@"'+$argumentFile+'"'+$identityArguments
    $process.StartInfo.WorkingDirectory=$directory
    $process.StartInfo.UseShellExecute=$false
    $process.StartInfo.CreateNoWindow=$true
    $process.StartInfo.RedirectStandardOutput=$true
    $process.StartInfo.RedirectStandardError=$true
    $process.StartInfo.RedirectStandardInput=$true
    $process.StartInfo.EnvironmentVariables['GRADLE_USER_HOME']=Join-Path $directory 'empty-gradle-home'
    foreach($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')){$process.StartInfo.EnvironmentVariables.Remove($name)}
    $context=[pscustomobject]@{
        Role=$Role;Directory=$directory;Process=$process;OutWriter=(New-LogWriter (Join-Path $directory 'qa-stdout.log'))
        ErrWriter=(New-LogWriter (Join-Path $directory 'qa-stderr.log'));OutTask=$null;ErrTask=$null;Characters=0;ExpectedExit=$false
        ServerSaved=$false;Failure=$false;StartUtc=$null
    }
    if(-not $process.Start()){$context.OutWriter.Dispose();$context.ErrWriter.Dispose();throw 'Owned native process failed to start.'}
    $actors[$Role]=$context
    $context.StartUtc=$process.StartTime.ToUniversalTime().ToString('o')
    $context.OutTask=$process.StandardOutput.ReadLineAsync()
    $context.ErrTask=$process.StandardError.ReadLineAsync()
    Write-Output "NETWORK_QA_STARTED role=$Role pid=$($process.Id)"
}
function Pump-Logs{
    $contexts=@($actors.Values);if($null -ne $traceContext){$contexts+=@($traceContext)}
    foreach($context in $contexts){
        foreach($channel in @('Out','Err')){
            for($batch=0;$batch -lt 64;$batch++){
                $task=$context.($channel+'Task')
                if($null -eq $task -or -not $task.IsCompleted){break}
                $line=$task.GetAwaiter().GetResult()
                if($null -eq $line){$context.($channel+'Task')=$null;break}
                $context.Characters+=$line.Length
                if($line.Length -gt 65536 -or $context.Characters -gt 16777216){throw 'Native log capture exceeded bounded limits.'}
                if($line -match 'accessToken|clientId|auth_|xuid'){$line='[REDACTED_LAUNCH_IDENTITY_LINE]'}
                $writer=$context.($channel+'Writer')
                $writer.WriteLine($line);$writer.Flush()
                # Consume the completed task before an oracle can reject the line.
                # Cleanup must drain the next line, not rethrow on the same observation.
                $reader=if($channel -eq 'Out'){$context.Process.StandardOutput}else{$context.Process.StandardError}
                $context.($channel+'Task')=$reader.ReadLineAsync()
                if($line -match 'LEGACYBRIDGE_HANDSHAKE_QA_FAILED|LEGACYBRIDGE_NETWORK_QA_FAILED|Failed to start the minecraft server|ModLoadingException'){$context.Failure=$true;$script:networkFailure=$true}
                if($line -match 'All dimensions are saved|All chunks are saved'){$context.ServerSaved=$true}
                # Exact handshake logs are checked separately after both actors close.
            }
        }
    }
}

function Wait-HandshakeData([string]$Role,[string]$Name,[int]$Seconds){
    $path=Join-Path $control ($Role+'/'+$Name+'.json')
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while(-not(Test-Path -LiteralPath $path)){
        Pump-Logs
        if($networkFailure -or $actors[$Role].Process.HasExited){throw 'Native handshake observer failed/exited before receipt.'}
        if($watch.Elapsed.TotalSeconds -gt $Seconds -or $globalWatch.Elapsed.TotalSeconds -gt 900){throw 'Native handshake receipt deadline; timeout is not rejection evidence.'}
        Start-Sleep -Milliseconds 20
    }
    Assert-Plain $path
    if((Get-Item -LiteralPath $path).Length -gt 16384){throw 'Native receipt exceeds bound.'}
    $data=Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
    $expectedStatus=if($Name -ceq 'ready'){'READY'}else{'PASSED'}
    if($data.schemaVersion -ne 1 -or $data.status -cne $expectedStatus -or $data.role -cne $Role -or $data.scenario -cne $Scenario -or $data.nativeMainThread -cne $true){throw 'Invalid or failed native handshake receipt.'}
    return $data
}
function Wait-HandshakeExit([string[]]$Roles,[int]$Seconds){
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while(@($Roles | Where-Object {-not $actors[$_].Process.HasExited -or $null -ne $actors[$_].OutTask -or $null -ne $actors[$_].ErrTask}).Count -gt 0){
        Pump-Logs
        if($watch.Elapsed.TotalSeconds -gt $Seconds){throw 'Owned native process shutdown deadline; do not claim cleanup.'}
        Start-Sleep -Milliseconds 20
    }
    foreach($role in $Roles){if($actors[$role].Process.ExitCode -ne 0 -or $actors[$role].Failure){throw 'Owned native process did not exit cleanly.'}}
}

# All inspection below is inert. The original is hashed, never installed or executed.
foreach($path in @($project,$profileRoot,$run,$control,$java)){Assert-Plain $path}
$inputScripts=@($PSCommandPath,(Join-Path $PSScriptRoot 'ProductionClient.Common.ps1'),
    (Join-Path $PSScriptRoot 'NetworkRuntime.Installation.ps1'),(Join-Path $PSScriptRoot 'NetworkHandshake.Diagnostics.ps1'),
    (Join-Path $PSScriptRoot 'NetworkHandshake.Control.ps1'))
$inputScripts+=@(Join-Path $PSScriptRoot 'NetworkHandshake.Trace.ps1')
$scriptEvidence=@($inputScripts | ForEach-Object { Assert-Plain $_; [ordered]@{path=$_.Substring($project.Length+1).Replace('\','/');sha256=(Hash $_)} })
$preparationPath=Join-Path $run 'preparation-receipt.json'
Assert-Plain $preparationPath
if((Get-Item -LiteralPath $preparationPath).Length -gt 4194304){throw 'Preparation inventory exceeds limit.'}
$prepared=Get-Content -LiteralPath $preparationPath -Raw -Encoding UTF8 | ConvertFrom-Json
if($prepared.schemaVersion -ne 1 -or $prepared.runLabel -cne $RunLabel -or $prepared.profile -cne 'network-snapshot-v1' -or
    $prepared.minecraft -cne '1.20.1' -or $prepared.forge -cne '47.4.23' -or $prepared.clientCount -ne 1 -or
    $prepared.operation -cne 'PREPARED_LOCAL_ORDINARY_INSTALLATIONS' -or @($prepared.files).Count -ne 3785){throw 'Use a fresh exact single-client preparation for this separate two-process scenario.'}
$preparedPaths=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach($file in $prepared.files){
    if(-not $preparedPaths.Add($file.path)){throw 'Duplicate preparation path.'}
    $path=Child $run $file.path; Assert-Plain $path
    if($file.sha256 -cnotmatch '^[0-9a-f]{64}$' -or (Hash $path) -cne $file.sha256 -or (Get-Item -LiteralPath $path).Length -ne $file.size){throw 'Prepared installation changed.'}
}
foreach($folder in @('server','client-a')){
    $directory=Join-Path $run $folder; Assert-Plain $directory
    foreach($name in @('mods','empty-user-home','empty-gradle-home','tmp','natives')){
        $path=Join-Path $directory $name; Assert-Plain $path
        if(@(Get-ChildItem -LiteralPath $path -Force).Count -ne 0){throw 'Prepared runtime directories must still be empty.'}
    }
    foreach($entry in @(Get-ChildItem -LiteralPath $directory -Force)){
        Assert-Plain $entry.FullName
        if($entry.Name -notin @('mods','empty-user-home','empty-gradle-home','tmp','natives','libraries','versions','assets','eula.txt')){throw 'Consumed or unknown installation retained; choose a fresh run.'}
    }
}
foreach($role in $runtimeRoles){if(@(Get-ChildItem -LiteralPath (Join-Path $control $role) -Force).Count -ne 0){throw 'Existing control evidence preserved.'}}
foreach($process in @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")){
    if($process.CommandLine -and $process.CommandLine.IndexOf($project,[StringComparison]::OrdinalIgnoreCase) -ge 0){throw 'Finish owned Java/build processes before this native test.'}
}
$freeKiB=[int64](Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory
$requiredFreeKiB=$caseContract.RequiredFreeKiB+$runMode.ExtraFreeKiB
if($freeKiB -lt $requiredFreeKiB){throw "HANDSHAKE_QA_PRESTART_BLOCKED requiredFreeKiB=$requiredFreeKiB actualFreeKiB=$freeKiB gameStarted=false"}
if(@(Get-NetTCPConnection -LocalPort 25591 -State Listen -ErrorAction SilentlyContinue).Count -ne 0){throw 'Closed loopback test port is occupied.'}
if($readTraceRequested -and @(Get-NetTCPConnection -LocalPort 25592 -State Listen -ErrorAction SilentlyContinue).Count -ne 0){throw 'Owned diagnostic port is occupied.'}
$server=Join-Path $run 'server'; $client=Join-Path $run 'client-a'
if((Hash (Join-Path $server 'libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt')) -cne '659e71b783f09de7cfc05591472191b73ea5321864412ce63f49013c266781b8'){throw 'Audited ordinary server args changed.'}
if((Get-Content -LiteralPath (Join-Path $server 'eula.txt') -Raw) -notmatch '(?m)^eula=true\s*$'){throw 'Previously accepted isolated-test EULA required.'}
if((Hash (Join-Path $client 'versions/1.20.1-forge-47.4.23/1.20.1-forge-47.4.23.json')) -cne '154923d5c1a51b669bf8a981693f068021312f2495a13bf87b277bc702d95c7b'){throw 'Exact ordinary client metadata changed.'}
Assert-ProductionHash (Join-Path $client 'versions/1.20.1/1.20.1.json') '19f5ae58f9c31bd3b0923cb822e99e3162bd62ab'
foreach($library in @(Read-NetworkInstallerLibraries (Join-Path $project 'build/p3-production/setup/forge-1.20.1-47.4.23-installer.jar'))){
    $relative='client-a/libraries/'+$library.path
    if(-not $preparedPaths.Contains($relative)){throw 'Missing FML discovery library.'}
    $path=Child $run $relative; Assert-Plain $path; Assert-ProductionHash $path $library.sha1
    if((Get-Item -LiteralPath $path).Length -ne $library.size){throw 'FML discovery size mismatch.'}
}
$original=Join-Path $project 'fixtures/network-snapshot-1.16.5/build/libs/legacybridge-network-1.16.5-1.0.0.jar'
$converted=Join-Path $profileRoot 'converted.jar'
$public=Join-Path $project 'legacy-product/build/libs/LegacyBridge.jar'
$probe=Join-Path $project 'fixtures/network-handshake-probe-1.20.1/build/libs/legacybridge-network-handshake-probe-1.20.1-1.0.0.jar'
$conversionPath=Join-Path $profileRoot 'conversion.json'
$pinnedInputs=@(
    @($original,'a5843c3dc1a0364a56220cceb9b1d3de8b0511d4941b2463e5a75ce5f838a33e'),
    @($converted,'18b00406492e6fb7ccba4ae799bda0165446b66fd538444619c1eeed69a3a91f'),
    @($public,'8daae3478a1b59482c438b843311c4160b5bdc8e4721f8e6067a1a87b81bbf88'),
    @($conversionPath,'529f972bbeb23f6f3ab56921f53dc098c389b15021e6b636b30b13a294ee66ae'))
foreach($entry in $pinnedInputs){Assert-Plain $entry[0];if((Hash $entry[0]) -cne $entry[1]){throw 'Reviewed static input/public baseline changed; explicit review required.'}}
Assert-Plain $probe; $probeHash=Hash $probe
if($readTraceRequested){Assert-Plain (Join-Path $project 'fixtures/network-handshake-probe-1.20.1/build/libs/legacybridge-network-read-trace-1.0.0.jar')}
Add-Type -AssemblyName System.IO.Compression.FileSystem
if((Read-ZipText $probe 'META-INF/mods.toml') -notmatch 'modId="legacybridge_handshake_probe"' -or
    (Read-ZipText $probe 'META-INF/mods.toml') -match 'modId="legacybridge_network"'){throw 'Wrong standalone handshake QA observer.'}
foreach($role in $runtimeRoles){
    $folder=if($role -ceq 'server'){'server'}else{'client-a'}
    $files=@($probe);if($caseContract.PublicRequired){$files+=@($public)}
    if(($role -ceq 'server' -and $caseContract.ServerFixture) -or ($role -ceq 'a' -and $caseContract.ClientFixture)){$files+=@($converted)}
    foreach($file in $files){Copy-Mod $file (Join-Path $run ($folder+'/mods/'+[IO.Path]::GetFileName($file)))}
}
$properties=@('server-ip=127.0.0.1','server-port=25591','online-mode=false','enforce-secure-profile=false','enable-rcon=false','enable-query=false',
    'view-distance=2','simulation-distance=2','spawn-protection=0','level-name=handshake-qa-world','level-seed=732091','generate-structures=false','max-players=1',
    'gamemode=creative','difficulty=peaceful','allow-flight=true')
Write-New (Join-Path $server 'server.properties') (($properties -join [Environment]::NewLine)+[Environment]::NewLine)
Write-New (Join-Path $client 'options.txt') ((@(Get-NetworkClientOptions) -join [Environment]::NewLine)+[Environment]::NewLine)
try{
    Start-Actor 'server'
    $serverReady=Wait-HandshakeData 'server' 'ready' 180
    Start-Actor 'a'
    if($readTraceRequested){Start-NetworkReadTrace $client $actors['a'].Process.Id $ReadClearTrace.IsPresent}
    $clientReady=Wait-HandshakeData 'a' 'ready' 240
    $clientResult=Wait-HandshakeData 'a' 'result' 150
    Wait-HandshakeExit @('a') 60
    $traceEvidence=if($readTraceRequested){Complete-NetworkReadTrace}else{$null}
    Write-NetworkHandshakeMarker (Join-Path $control 'server/finish.request')
    $serverResult=Wait-HandshakeData 'server' 'result' 120
    if($PacketRecording){foreach($role in $runtimeRoles){
        $path=Join-Path $control ($role+'/packet-diagnostic.json');Assert-Plain $path
        if((Get-Item -LiteralPath $path).Length -gt 16384){throw 'Recording receipt exceeds bound.'}
        $data=Get-Content -LiteralPath $path -Raw -Encoding UTF8 | ConvertFrom-Json
        Assert-NetworkHandshakeRecording $data $role $Scenario
    }}
    Assert-NetworkHandshakeObservations $caseContract $clientResult $serverResult
    $actors['server'].Process.StandardInput.WriteLine('stop');$actors['server'].Process.StandardInput.Flush()
    Wait-HandshakeExit $runtimeRoles 90
    if(-not $actors['server'].ServerSaved){throw 'Native dedicated-server save marker missing.'}
    foreach($context in $actors.Values){$context.OutWriter.Dispose();$context.ErrWriter.Dispose()}
    $nativeLogs=@{}
    foreach($role in $runtimeRoles){
        $debug=Join-Path $actors[$role].Directory 'logs/debug.log'; Assert-Plain $debug
        if((Get-Item -LiteralPath $debug).Length -gt 16777216){throw 'Native debug log exceeds bounded audit.'}
        $nativeLogs[$role]=@(Get-Content -LiteralPath $debug -Encoding UTF8)
    }
    $nativeOracle=Assert-NetworkHandshakeLogs $caseContract $nativeLogs['a'] $nativeLogs['server']
    foreach($entry in $pinnedInputs){if((Hash $entry[0]) -cne $entry[1]){throw 'Original/static/public input changed during runtime.'}}
    if((Hash $probe) -cne $probeHash){throw 'Native QA artifact changed.'}
    foreach($entry in $inventory){if((Hash (Child $run $entry.path)) -cne $entry.sha256){throw 'Installed JAR changed.'}}
    foreach($entry in $scriptEvidence){if((Hash (Child $project $entry.path)) -cne $entry.sha256){throw 'Executed QA script changed during run.'}}
    $processes=@(); $receipts=@()
    foreach($role in $runtimeRoles){
        $actor=$actors[$role]
        $processes+=[ordered]@{role=$role;pid=$actor.Process.Id;startUtc=$actor.StartUtc;exitCode=$actor.Process.ExitCode
            stdoutSha256=(Hash (Join-Path $actor.Directory 'qa-stdout.log'));stderrSha256=(Hash (Join-Path $actor.Directory 'qa-stderr.log'))
            debugSha256=(Hash (Join-Path $actor.Directory 'logs/debug.log'));safeArgumentsSha256=(Hash (Join-Path $actor.Directory 'qa-safe-arguments.txt'))}
        $receiptNames=@('ready','result');if($PacketRecording){$receiptNames+=@('packet-diagnostic')}
        foreach($name in $receiptNames){$relative='control/'+$role+'/'+$name+'.json';$receipts+=[ordered]@{path=$relative;sha256=(Hash (Child $run $relative))}}
    }
    $receipt=[ordered]@{
        schemaVersion=1;reportType='LEGACYBRIDGE_NATIVE_HANDSHAKE';status=$runMode.SuccessStatus;runLabel=$RunLabel;scenario=$Scenario
        diagnosticMode=$runMode.Name;normalModePassClaimed=$runMode.NativePassEligible
        platformBaseline=$caseContract.PlatformBaseline;publicInstalled=$caseContract.PublicRequired
        socketSnapshotRequested=$SocketSnapshot.IsPresent;packetRecordingRequested=$PacketRecording.IsPresent
        readInterestTraceRequested=$ReadInterestTrace.IsPresent;readClearTraceRequested=$ReadClearTrace.IsPresent;readInterestTrace=$traceEvidence
        minecraft='1.20.1';forge='47.4.23';javaSha256=(Hash $java);elapsedSeconds=[int]$globalWatch.Elapsed.TotalSeconds
        originalSha256=$pinnedInputs[0][1];convertedSha256=$pinnedInputs[1][1];publicSha256=$pinnedInputs[2][1];probeSha256=$probeHash
        preparationSha256=(Hash $preparationPath);scripts=$scriptEvidence;installedArtifacts=@($inventory.ToArray())
        caseContract=$caseContract;client=$clientResult;server=$serverResult;nativeDiagnosticOracle=$nativeOracle
        processes=$processes;receipts=$receipts;originalIntact=$true;nativeCleanShutdown=$true;gradleRuntimeClasspath=$false
        sourceRuntimeComparison='NOT_RUN';publicOnlyGui='NOT_RUN';offlineGame='NOT_TESTED';crossVersionConnection='UNSUPPORTED'
    }
    $identity=Get-NetworkRuntimeIdentityEvidence $true;foreach($key in $identity.Keys){$receipt[$key]=$identity[$key]}
    Write-New (Join-Path $run 'handshake-receipt.json') (($receipt|ConvertTo-Json -Depth 12)+[Environment]::NewLine)
    $passed=$true
    Write-Output "LEGACYBRIDGE_NATIVE_HANDSHAKE_$($runMode.SuccessStatus) scenario=$Scenario runLabel=$RunLabel mode=$($runMode.Name)"
}finally{
    # Detach/resume before requesting ordinary native shutdown. No VM exit/kill.
    $traceCleanupFailed=$false
    try{Stop-NetworkReadTrace}catch{$traceCleanupFailed=$true;Write-Warning 'Diagnostic detach failed; native cleanup will still be attempted and no cleanup PASS claimed.'}
    if(-not $passed){
        foreach($actor in $actors.Values){
            if(-not $actor.Process.HasExited){
                if($actor.Role -ceq 'server'){$actor.Process.StandardInput.WriteLine('stop');$actor.Process.StandardInput.Flush()}
                else{$stop=Join-Path $control 'a/stop.request';if(-not(Test-Path -LiteralPath $stop)){Write-NetworkHandshakeMarker $stop}}
            }
        }
        $cleanup=[Diagnostics.Stopwatch]::StartNew()
        while(@($actors.Values | Where-Object {-not $_.Process.HasExited -or $null -ne $_.OutTask -or $null -ne $_.ErrTask}).Count -gt 0 -and $cleanup.Elapsed.TotalSeconds -lt 60){
            Pump-Logs;Start-Sleep -Milliseconds 20
        }
        $remaining=@($actors.Values | Where-Object {-not $_.Process.HasExited} | ForEach-Object {$_.Process.Id})
        $traceRemaining=if($null -ne $traceContext -and -not $traceContext.Process.HasExited){@($traceContext.Process.Id)}else{@()}
        Write-New (Join-Path $run 'handshake-failure.json') (([ordered]@{schemaVersion=1;status='FAILED_OR_INCOMPLETE';scenario=$Scenario;runLabel=$RunLabel;diagnosticMode=$runMode.Name;remainingOwnedProcesses=$remaining;remainingTraceProcesses=$traceRemaining;traceCleanupFailed=$traceCleanupFailed;runtimePassClaimed=$false}|ConvertTo-Json)+[Environment]::NewLine)
    }
    foreach($actor in $actors.Values){if($actor.Process.HasExited){$actor.OutWriter.Dispose();$actor.ErrWriter.Dispose();$actor.Process.Dispose()}}
    if($null -ne $traceContext -and $traceContext.Process.HasExited){$traceContext.Process.Dispose()}
    if($traceCleanupFailed){throw 'Diagnostic detach failed; inspect the retained failure evidence.'}
}

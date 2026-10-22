# Explicit runtime QA: dedicated server plus two clients by default; -ServerOnly proves discovery/clean shutdown only.
# No launcher-account reads, original execution, hidden download, build, or public upload.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z0-9][a-z0-9-]{0,31}$')][string]$RunLabel,
    [switch]$ServerOnly
)
$ErrorActionPreference='Stop'
$ValidationRun='p6-public'
. (Join-Path $PSScriptRoot 'ProductionClient.Common.ps1')
. (Join-Path $PSScriptRoot 'NetworkRuntime.Diagnostics.ps1')
. (Join-Path $PSScriptRoot 'NetworkRuntime.Installation.ps1')
$project=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$profileRoot=Join-Path $project 'build/network-snapshot-v1'
$run=Join-Path $profileRoot $RunLabel
$control=Join-Path $run 'control'
$java='C:/Program Files/Java/jdk-17/bin/java.exe'
$actors=@{}
$steps=[Collections.Generic.List[object]]::new()
$counts=@{server=0;a=0;b=0}
$inventory=[Collections.Generic.List[object]]::new()
$globalWatch=[Diagnostics.Stopwatch]::StartNew()
$networkFailure=$false
$mode=if($ServerOnly){'SINGLE_SERVER_ONLY'}else{'TWO_CLIENT_NETWORK'}
$runtimeRoles=if($ServerOnly){@('server')}else{@('server','a','b')}
$runtimeFolders=if($ServerOnly){@('server')}else{@('server','client-a','client-b')}
Write-Output "NETWORK_QA_PREFLIGHT mode=$mode runLabel=$RunLabel gameStarted=false"

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
    if($actors.ContainsKey($Role) -or $actors.Count -ge 3){throw 'At most the three explicit QA roles may be launched.'}
    $folder=if($Role -eq 'server'){'server'}else{'client-'+$Role}
    $directory=Join-Path $run $folder
    $arguments=@('-Xms256m',$(if($Role -eq 'server'){'-Xmx1536m'}else{'-Xmx1792m'}),'-Dfile.encoding=UTF-8',
        '-Djavax.net.ssl.trustStore=NONE','-Djavax.net.ssl.trustStoreType=Windows-ROOT',
        ('-Djava.io.tmpdir='+(Join-Path $directory 'tmp')),('-Duser.home='+(Join-Path $directory 'empty-user-home')),
        ('-Dlegacybridge.netqa.role='+$Role),('-Dlegacybridge.netqa.control='+$control))
    $identityArguments=''
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
        ServerSaved=$false;NativeUnknownIdCount=0;NativeUnknownIdNettyCount=0;NativeUnknownIdRenderCount=0
        NativeUnknownFallbackCount=0;NativeWrongDirectionCount=0;Failure=$false;StartUtc=$null
        UnknownIdWindow=$false;WrongDirectionWindow=$false
    }
    if(-not $process.Start()){$context.OutWriter.Dispose();$context.ErrWriter.Dispose();throw 'Owned native process failed to start.'}
    $actors[$Role]=$context
    $context.StartUtc=$process.StartTime.ToUniversalTime().ToString('o')
    $context.OutTask=$process.StandardOutput.ReadLineAsync()
    $context.ErrTask=$process.StandardError.ReadLineAsync()
    Write-Output "NETWORK_QA_STARTED role=$Role pid=$($process.Id)"
}
function Pump-Logs{
    foreach($context in @($actors.Values)){
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
                if($line -match 'LEGACYBRIDGE_NETWORK_QA_FAILED|Failed to start the minecraft server|ModLoadingException'){$context.Failure=$true;$script:networkFailure=$true}
                if($line -match 'All dimensions are saved|All chunks are saved'){$context.ServerSaved=$true}
                Update-NetworkDiagnosticObservation $context $line
            }
        }
    }
}
function Wait-Receipt([string]$Role,[int]$Id,[int]$Seconds=120){
    $path=Join-Path $control ($Role+'/'+('receipt-{0:d4}.json' -f $Id))
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while(-not(Test-Path -LiteralPath $path)){
        Pump-Logs
        if($networkFailure){throw 'Native QA failure marker observed; inspect owned logs.'}
        if($actors[$Role].Process.HasExited){throw "Native role $Role exited before receipt $Id."}
        if($watch.Elapsed.TotalSeconds -gt $Seconds -or $globalWatch.Elapsed.TotalSeconds -gt 900){throw "Native role $Role receipt deadline $Id."}
        Start-Sleep -Milliseconds 20
    }
    Assert-Plain $path
    if((Get-Item -LiteralPath $path).Length -gt 16384){throw 'Receipt exceeds bound.'}
    $response=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
    if($response.schemaVersion -ne 1 -or $response.role -ne $Role -or $response.id -ne $Id -or $response.status -ne $(if($Id -eq 0){'READY'}else{'PASSED'})){throw "Native role $Role rejected command $Id; inspect its structured receipt."}
    if($Id -ne 0 -and $response.nativeMainThread -ne $true){throw 'Native observation did not execute on its actual main thread.'}
    $steps.Add([ordered]@{role=$Role;id=$Id;path=$path.Substring($run.Length+1).Replace('\','/');sha256=(Hash $path);observation=$response})
    return $response
}
function Command([string]$Role,[string]$Action,[hashtable]$Fields=@{}){
    $counts[$Role]++
    if($counts[$Role] -gt 96){throw 'Finite command limit exceeded.'}
    $request=[ordered]@{id=$counts[$Role];action=$Action}
    foreach($key in $Fields.Keys){$request[$key]=$Fields[$key]}
    $json=$request|ConvertTo-Json -Compress
    if([Text.Encoding]::UTF8.GetByteCount($json) -gt 2048){throw 'Command exceeds bound.'}
    $base=Join-Path $control ($Role+'/'+('command-{0:d4}' -f $counts[$Role]))
    Write-New ($base+'.tmp') ($json+[Environment]::NewLine)
    [IO.File]::Move(($base+'.tmp'),($base+'.json'))
    $response=Wait-Receipt $Role $counts[$Role]
    if($response.action -ne $Action){throw 'Response action does not match the submitted command.'}
    return $response
}
function Expect([string]$Role,[int]$Sequence,[int]$Value,[int]$Accepted,[int]$Rejected,[bool]$Connected=$true){
    $result=Command $Role 'EXPECT' @{sequence=$Sequence;value=$Value;accepted=$Accepted;rejected=$Rejected;connected=$Connected}
    if($result.sequence -ne $Sequence -or $result.value -ne $Value -or $result.accepted -ne $Accepted -or $result.rejected -ne $Rejected -or $result.connected -ne $Connected -or $result.integratedServer -ne $false){throw 'Native snapshot differs from the independent scenario oracle.'}
    if($Connected -and $result.nativeLoopback -ne $true){throw 'Expected a real loopback transport.'}
    return $result
}
function Peers([bool]$A,[bool]$B){
    $result=Command 'server' 'EXPECT_PEERS' @{a=$A;b=$B}
    if($result.a -ne $A -or $result.b -ne $B -or $result.players -ne ([int]$A+[int]$B) -or $result.dedicatedServer -ne $true -or $result.allConnectionsNativeLoopback -ne $true -or $result.accepted -ne 0 -or $result.rejected -ne 0){throw 'Actual dedicated-server player isolation differs.'}
    return $result
}
function Send-Snapshot([string]$Role,[int]$Sequence,[int]$Value){$null=Command 'server' 'SEND' @{target=$Role;sequence=$Sequence;value=$Value}}
function Raw-S2C([string]$Role,[string]$Hex){$null=Command 'server' 'RAW_S2C' @{target=$Role;payload=$Hex}}

# Preflight is entirely read-only until every prepared input and approved artifact matches.
foreach($path in @($project,$profileRoot,$run,$control,$java)){Assert-Plain $path}
$diagnosticOraclePath=Join-Path $PSScriptRoot 'NetworkRuntime.Diagnostics.ps1'
Assert-Plain $diagnosticOraclePath
$orchestratorHash=Hash $PSCommandPath
$diagnosticOracleHash=Hash $diagnosticOraclePath
$installationOraclePath=Join-Path $PSScriptRoot 'NetworkRuntime.Installation.ps1'
Assert-Plain $installationOraclePath
$installationOracleHash=Hash $installationOraclePath
$discoveryLibraries=@(Read-NetworkInstallerLibraries (Join-Path $project 'build/p3-production/setup/forge-1.20.1-47.4.23-installer.jar'))
$preparationPath=Join-Path $run 'preparation-receipt.json'
Assert-Plain $preparationPath
if((Get-Item -LiteralPath $preparationPath).Length -gt 4194304){throw 'Preparation inventory exceeds bound.'}
$prepared=Get-Content -LiteralPath $preparationPath -Raw|ConvertFrom-Json
if($prepared.schemaVersion -ne 1 -or $prepared.profile -ne 'network-snapshot-v1' -or $prepared.runLabel -ne $RunLabel -or $prepared.operation -ne 'PREPARED_LOCAL_ORDINARY_INSTALLATIONS' -or $prepared.minecraft -ne '1.20.1' -or $prepared.forge -ne '47.4.23'){throw 'Prepared run identity differs.'}
if(@($prepared.files).Count -lt 7000 -or @($prepared.files).Count -gt 10000){throw 'Unexpected bounded ordinary-installation inventory size.'}
$preparedPaths=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach($file in @($prepared.files)){
    if(-not $preparedPaths.Add($file.path)){throw 'Duplicate prepared file identity.'}
    $path=Child $run $file.path
    Assert-Plain $path
    if($file.sha256 -notmatch '^[0-9a-f]{64}$' -or (Hash $path) -ne $file.sha256 -or (Get-Item -LiteralPath $path).Length -ne $file.size){throw 'Prepared installation file changed.'}
}
foreach($role in @('server','a','b')){
    $folder=if($role -eq 'server'){'server'}else{'client-'+$role}
    $directory=Join-Path $run $folder
    foreach($empty in @('mods','empty-user-home','empty-gradle-home','tmp','natives')){
        $path=Join-Path $directory $empty
        Assert-Plain $path
        if(@(Get-ChildItem -LiteralPath $path -Force).Count -ne 0){throw 'Expected fresh empty mod/user/Gradle/temp/native directories.'}
    }
    if(@(Get-ChildItem -LiteralPath (Join-Path $control $role) -Force).Count -ne 0){throw 'Prior/partial control evidence preserved: choose a fresh run.'}
    foreach($entry in @(Get-ChildItem -LiteralPath $directory -Force)){
        if($entry.Name -notin @('mods','empty-user-home','empty-gradle-home','tmp','natives','libraries','versions','assets','eula.txt')){throw 'Prior world/options/log or unknown run file exists.'}
        Assert-Plain $entry.FullName
    }
}
foreach($process in @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")){
    if($process.CommandLine -and $process.CommandLine.IndexOf($project,[StringComparison]::OrdinalIgnoreCase) -ge 0){throw 'Stop owned Java/build operations first; only this orchestrator may own the three concurrent QA processes.'}
}
$requiredMemoryKiB=if($ServerOnly){3145728}else{6291456}
$freeMemoryKiB=[int64](Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory
if($freeMemoryKiB -lt $requiredMemoryKiB){throw "NETWORK_QA_PRESTART_BLOCKED mode=$mode requiredFreeKiB=$requiredMemoryKiB actualFreeKiB=$freeMemoryKiB gameStarted=false; do not weaken the memory gate."}
if(@(Get-NetTCPConnection -LocalPort 25591 -State Listen -ErrorAction SilentlyContinue).Count -ne 0){throw 'The closed loopback QA port is already occupied.'}
$server=Join-Path $run 'server'
$serverArgs=Join-Path $server 'libraries/net/minecraftforge/forge/1.20.1-47.4.23/win_args.txt'
if((Hash $serverArgs) -ne '659e71b783f09de7cfc05591472191b73ea5321864412ce63f49013c266781b8'){throw 'Exact audited ordinary server argument file changed; do not forward it.'}
if((Get-Content -LiteralPath (Join-Path $server 'eula.txt') -Raw) -notmatch '(?m)^eula=true\s*$'){throw 'Already accepted isolated-test EULA is missing.'}
foreach($role in @('a','b')){
    $client=Join-Path $run ('client-'+$role)
    if((Hash (Join-Path $client 'versions/1.20.1-forge-47.4.23/1.20.1-forge-47.4.23.json')) -ne '154923d5c1a51b669bf8a981693f068021312f2495a13bf87b277bc702d95c7b'){throw 'Exact audited ordinary client metadata changed.'}
    Assert-ProductionHash (Join-Path $client 'versions/1.20.1/1.20.1.json') '19f5ae58f9c31bd3b0923cb822e99e3162bd62ab'
    if(-not $ServerOnly){
        foreach($library in $discoveryLibraries){
            $relative='client-'+$role+'/libraries/'+$library.path
            if(-not $preparedPaths.Contains($relative)){throw 'Preparation omitted a mandatory FML-discovered client library.'}
            $path=Child $run $relative
            Assert-Plain $path
            Assert-ProductionHash $path $library.sha1
            if((Get-Item -LiteralPath $path).Length -ne $library.size){throw 'FML-discovered library size changed.'}
        }
    }
}
$original=Join-Path $project 'fixtures/network-snapshot-1.16.5/build/libs/legacybridge-network-1.16.5-1.0.0.jar'
$converted=Join-Path $profileRoot 'converted.jar'
$conversionPath=Join-Path $profileRoot 'conversion.json'
$public=Join-Path $project 'legacy-product/build/libs/LegacyBridge.jar'
$probe=Join-Path $project 'fixtures/network-runtime-probe-1.20.1/build/libs/legacybridge-network-probe-1.20.1-1.0.0.jar'
foreach($path in @($original,$converted,$conversionPath,$public,$probe)){Assert-Plain $path}
$originalHash=Hash $original
$convertedHash=Hash $converted
$publicHash=Hash $public
$probeHash=Hash $probe
if((Get-Item -LiteralPath $conversionPath).Length -gt 4194304){throw 'Conversion report exceeds bound.'}
$conversion=Get-Content -LiteralPath $conversionPath -Raw|ConvertFrom-Json
if($conversion.schemaVersion -ne 2 -or $conversion.reportType -ne 'LEGACY_CONVERSION' -or $conversion.status -ne 'CONVERTED' -or $conversion.transformationsCommitted -ne $true -or
    $conversion.input.sha256 -ne $originalHash -or $conversion.output.sha256 -ne $convertedHash -or $conversion.bytecodeValidation.status -ne 'PASSED' -or
    $conversion.bytecodeValidation.modClassesLoaded -ne $false -or $conversion.modId -ne 'legacybridge_network' -or $conversion.modVersion -ne '1.0.0' -or
    $conversion.networkContract.profile -ne 'network-snapshot-v1' -or $conversion.networkContract.direction -ne 'PLAY_TO_CLIENT' -or
    $conversion.networkContract.discriminator -ne 0 -or $conversion.networkContract.payloadBytes -ne 8 -or $conversion.runtimeRequired -ne 'legacybridge_runtime'){throw 'The exact input/output/profile lacks approved static validation.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$manifest=Read-ZipText $public 'META-INF/MANIFEST.MF'
$runtimeMetadata=Read-ZipText $public 'META-INF/mods.toml'
if($manifest -notmatch ('(?m)^LegacyBridge-Adapter-Version: '+[regex]::Escape($conversion.runtimeVersion)+'\r?$') -or
    $runtimeMetadata -notmatch 'modId="legacybridge_runtime"' -or $runtimeMetadata -notmatch ('version="'+[regex]::Escape($conversion.runtimeVersion)+'"')){throw 'Public runtime identity/adapter version does not match the converted artifact.'}
foreach($folder in $runtimeFolders){
    foreach($file in @($converted,$public,$probe)){Copy-Mod $file (Join-Path $run ($folder+'/mods/'+[IO.Path]::GetFileName($file)))}
}
$properties=@('server-ip=127.0.0.1','server-port=25591','online-mode=false','enforce-secure-profile=false','enable-rcon=false','enable-query=false',
    'view-distance=2','simulation-distance=2','spawn-protection=0','level-name=network-qa-world','level-seed=732091','generate-structures=false','max-players=2',
    'gamemode=creative','difficulty=peaceful','allow-flight=true')
Write-New (Join-Path $server 'server.properties') (($properties -join [Environment]::NewLine)+[Environment]::NewLine)
$options=@(Get-NetworkClientOptions)
if(-not $ServerOnly){foreach($role in @('a','b')){Write-New (Join-Path $run ('client-'+$role+'/options.txt')) (($options -join [Environment]::NewLine)+[Environment]::NewLine)}}
$passed=$false
try{
    Start-Actor 'server'
    $null=Wait-Receipt 'server' 0 180
    if($ServerOnly){
        # Actual discovered fixture, zero players/counters on the server thread; no packets are sent.
        $null=Peers $false $false
    }else{
    Start-Actor 'a'
    Start-Actor 'b'
    $null=Wait-Receipt 'a' 0 240
    $null=Wait-Receipt 'b' 0 240
    $null=Command 'a' 'CONNECT'
    $null=Expect 'a' 0 0 0 0
    $null=Command 'b' 'CONNECT'
    $null=Expect 'b' 0 0 0 0
    $null=Peers $true $true
    Send-Snapshot 'a' 1 101
    Send-Snapshot 'b' 1 202
    $null=Expect 'a' 1 101 1 0
    $null=Expect 'b' 1 202 1 0

    Send-Snapshot 'a' 1 111
    $null=Expect 'a' 1 101 1 1 # duplicate did not mutate the current value
    Send-Snapshot 'a' 3 333
    $null=Expect 'a' 1 101 1 2 # gap did not advance the sequence
    Send-Snapshot 'a' 2 0
    $null=Expect 'a' 2 0 2 2 # legitimate zero is distinguished from closed state by sequence=2

    Raw-S2C 'a' '0000000003'           # ID=0, truncated four-byte body
    $null=Expect 'a' 2 0 2 3
    Raw-S2C 'a' '00000000030000012f00' # ID=0, nine-byte body: trailing byte
    $null=Expect 'a' 2 0 2 4
    Raw-S2C 'a' '0000000003ffffffff'   # negative value
    $null=Expect 'a' 2 0 2 5
    Raw-S2C 'a' '0000000003000003e9'   # value=1001
    $null=Expect 'a' 2 0 2 6
    Raw-S2C 'a' '000000000000000001'   # sequence=0
    $null=Expect 'a' 2 0 2 7
    Pump-Logs
    $actors['a'].UnknownIdWindow=$true
    Raw-S2C 'a' 'ff'                 # Pinned Forge: Netty ERROR, replayed Render ERROR, then vanilla Render WARN.
    Send-Snapshot 'a' 3 303          # ordered native barrier: preceding payload has already been processed
    $null=Expect 'a' 3 303 3 7
    $null=Expect 'b' 1 202 1 0       # A's errors never update B

    $disconnected=Command 'a' 'DISCONNECT'
    if($disconnected.connected -ne $false -or $disconnected.sequence -ne 0 -or $disconnected.value -ne 0 -or
        $disconnected.accepted -ne 3 -or $disconnected.rejected -ne 7 -or $disconnected.logouts -lt 1){throw 'Native disconnect did not close the visible fixture session.'}
    $null=Peers $false $true
    Send-Snapshot 'b' 2 222
    $null=Expect 'b' 2 222 2 0
    $null=Command 'a' 'CONNECT'
    $null=Peers $true $true
    $null=Expect 'a' 0 0 3 7         # the old closed-connection state is not reused
    Send-Snapshot 'a' 2 999
    $null=Expect 'a' 0 0 3 8        # a fresh session must start at 1, not resume at the old sequence
    Send-Snapshot 'a' 1 404
    $reconnected=Expect 'a' 1 404 4 8
    if($reconnected.logins -ne 2 -or $reconnected.logouts -lt 1){throw 'Actual login events do not prove reconnection.'}
    $null=Expect 'b' 2 222 2 0
    Send-Snapshot 'b' 3 333
    $null=Expect 'b' 3 333 3 0

    Pump-Logs
    $actors['server'].WrongDirectionWindow=$true
    $null=Command 'a' 'RAW_C2S' @{payload='0000000001000001f4'}
    $null=Expect 'a' 0 0 4 8 $false # actual Forge direction validation closes A before decoder/handler
    $null=Peers $false $true
    Send-Snapshot 'b' 4 444
    $null=Expect 'b' 4 444 4 0      # server and unrelated connection remain usable
    $null=Command 'b' 'DISCONNECT'
    $null=Expect 'b' 0 0 4 0 $false
    $null=Peers $false $false
    }
    $shutdownRoles=if($ServerOnly){@('server')}else{@('a','b','server')}
    foreach($role in $shutdownRoles){
        $actors[$role].ExpectedExit=$true
        $null=Command $role 'STOP'
    }
    $shutdown=[Diagnostics.Stopwatch]::StartNew()
    while(@($actors.Values|Where-Object{-not $_.Process.HasExited -or $null -ne $_.OutTask -or $null -ne $_.ErrTask}).Count -gt 0){
        Pump-Logs
        if($shutdown.Elapsed.TotalSeconds -gt 90){throw 'Native shutdown deadline; retain owned PID evidence, do not claim restart/shutdown success.'}
        Start-Sleep -Milliseconds 20
    }
    foreach($context in @($actors.Values)){
        $context.Process.WaitForExit()
        if($context.Process.ExitCode -ne 0 -or $context.Failure){throw 'An actual native process failed despite earlier packet observations.'}
        $context.OutWriter.Dispose();$context.ErrWriter.Dispose()
    }
    if(-not $actors['server'].ServerSaved){throw 'Native dedicated-server clean save/stop marker is absent.'}
    if(-not $ServerOnly){
    if($actors['a'].NativeUnknownIdCount -ne 2 -or $actors['a'].NativeUnknownIdNettyCount -ne 1 -or
        $actors['a'].NativeUnknownIdRenderCount -ne 1 -or $actors['a'].NativeUnknownFallbackCount -ne 1){
        throw 'Exact native unknown-ID sequence requires one Netty ERROR, one Render ERROR and one vanilla Render WARN.'
    }
    if($actors['server'].NativeWrongDirectionCount -ne 1){throw 'Exactly one native LegacyNetA direction-disconnect diagnostic is required after injection.'}
    $aErrors=(Get-Content -LiteralPath (Join-Path $run 'client-a/qa-stdout.log') -Raw)+[Environment]::NewLine+
        (Get-Content -LiteralPath (Join-Path $run 'client-a/qa-stderr.log') -Raw)
    foreach($expectation in @(@('NETWORK_OUT_OF_ORDER',2),@('NETWORK_INVALID_PAYLOAD',5),@('NETWORK_SESSION_MUST_START_AT_ONE',1))){
        # Minecraft may wrap System.err in LoggedPrintStream; require the exact terminal code,
        # while allowing the real native logger prefix rather than inventing a raw stderr layout.
        if([regex]::Matches($aErrors,('(?m)\b'+$expectation[0]+'\r?$')).Count -ne $expectation[1]){
            throw 'Exact fixture diagnostic counts do not match the bounded negative-packet oracle.'
        }
    }
    }
    if((Hash $original) -ne $originalHash -or (Hash $converted) -ne $convertedHash -or (Hash $public) -ne $publicHash -or (Hash $probe) -ne $probeHash){throw 'A source or runtime input changed during the native test.'}
    if((Hash $PSCommandPath) -ne $orchestratorHash -or (Hash $diagnosticOraclePath) -ne $diagnosticOracleHash){throw 'The runtime orchestrator or diagnostic oracle changed during the native test.'}
    if((Hash $installationOraclePath) -ne $installationOracleHash){throw 'The installation oracle changed during the native test.'}
    foreach($file in $inventory){
        $path=Child $run $file.path
        Assert-Plain $path
        if((Hash $path) -ne $file.sha256){throw 'An installed runtime JAR changed.'}
    }
    $processEvidence=@()
    foreach($role in $runtimeRoles){
        $context=$actors[$role]
        $processEvidence+=[ordered]@{
            role=$role;pid=$context.Process.Id;startUtc=$context.StartUtc;exitCode=$context.Process.ExitCode
            stdoutSha256=(Hash (Join-Path $context.Directory 'qa-stdout.log'))
            stderrSha256=(Hash (Join-Path $context.Directory 'qa-stderr.log'))
            safeArgumentsSha256=(Hash (Join-Path $context.Directory 'qa-safe-arguments.txt'))
        }
    }
    $receipt=[ordered]@{
        schemaVersion=2;profile='network-snapshot-v1';runLabel=$RunLabel;minecraft='1.20.1';forge='47.4.23';javaSha256=(Hash $java)
        originalFixtureSha256=$originalHash;convertedSha256=$convertedHash;publicSha256=$publicHash;probeSha256=$probeHash
        conversionReportSha256=(Hash $conversionPath);preparationSha256=(Hash $preparationPath);originalIntact=$true
        orchestratorSha256=$orchestratorHash;diagnosticOracleSha256=$diagnosticOracleHash
        installationOracleSha256=$installationOracleHash;installerSha256='75cfcb11f60cc641dc83757c1357c08c9bdba255ec529feb115c5e89a59cf75a'
        installedArtifacts=@($inventory.ToArray());processes=$processEvidence;steps=@($steps.ToArray())
        mode=$mode;cleanShutdown='PASSED';sourceRuntimeComparison='NOT_RUN'
        protocolMismatch='NOT_RUN';missingPeerMod='NOT_RUN';queuedCloseRace='NOT_RUN';counterSaturation='NOT_RUN'
        clientVisual='NOT_RUN';guiInput='NOT_RUN';wan='NOT_RUN';soak='NOT_RUN';offlineGame='NOT_TESTED'
        gradleRuntimeClasspath=$false;elapsedSeconds=[int]$globalWatch.Elapsed.TotalSeconds
    }
    # Native Forge debug logs may retain synthetic player names/UUIDs. Scope each
    # statement to the actual launcher-input/argument-file/console behavior.
    $identityEvidence=Get-NetworkRuntimeIdentityEvidence (-not $ServerOnly)
    foreach($key in $identityEvidence.Keys){$receipt[$key]=$identityEvidence[$key]}
    $scope=if($ServerOnly){[ordered]@{
        status='SINGLE_SERVER_ONLY';nativeDedicatedServer='PASSED';nativeServerFixtureState='ZERO_COUNTERS_NO_PLAYERS'
        nativeTwoClientS2c='NOT_RUN';nativeMainThreadApplication='NOT_RUN';packetDelivery='NOT_RUN';behavior='NOT_RUN'
    }}else{[ordered]@{
        status='TWO_CLIENT_NETWORK_PASSED'
        nativeDedicatedServer='PASSED';nativeTwoClientS2c='PASSED';nativeMainThreadApplication='PASSED'
        isolatedPlayerDelivery='PASSED';legitimateZeroValue='PASSED';duplicateAndGapRejection='PASSED'
        malformedWidthTrailingAndRange='PASSED';unknownDiscriminator='LOGGED_IGNORED_AND_FOLLOWUP_DELIVERED'
        nativeDisconnectAndReconnect='PASSED';freshSessionSequence='PASSED';wrongDirection='NATIVE_FORGE_DISCONNECT_BEFORE_HANDLER'
        unrelatedConnectionSurvives='PASSED'
        nativeDiagnosticOracle=[ordered]@{
            schemaVersion=2;channel='legacybridge_network:snapshot';unknownId=255
            clientAUnknownIdCount=$actors['a'].NativeUnknownIdCount
            clientANettyUnknownIdCount=$actors['a'].NativeUnknownIdNettyCount
            clientARenderUnknownIdCount=$actors['a'].NativeUnknownIdRenderCount
            clientARenderFallbackCount=$actors['a'].NativeUnknownFallbackCount
            unknownIdSequence='NETTY_ERROR_RENDER_ERROR_RENDER_WARN'
            serverDirectionDisconnectCount=$actors['server'].NativeWrongDirectionCount
            exactTerminalMessages=$true;injectionWindowsEnforced=$true
        }
    }}
    foreach($key in $scope.Keys){$receipt[$key]=$scope[$key]}
    $receiptName=if($ServerOnly){'network-server-receipt.json'}else{'network-receipt.json'}
    Write-New (Join-Path $run $receiptName) (($receipt|ConvertTo-Json -Depth 12)+[Environment]::NewLine)
    $passed=$true
    if($ServerOnly){Write-Output "LEGACYBRIDGE_NATIVE_SINGLE_SERVER_ONLY_PASSED runLabel=$RunLabel packetDelivery=NOT_RUN behavior=NOT_RUN"}
    else{Write-Output "LEGACYBRIDGE_NATIVE_TWO_CLIENT_NETWORK_PASSED runLabel=$RunLabel steps=$($steps.Count)"}
}finally{
    if(-not $passed){
        # Only exact Process objects created above. No taskkill, broad process scans, launcher/profile changes or forced kill.
        foreach($context in @($actors.Values)){
            if(-not $context.Process.HasExited){
                try{
                    if($context.Role -eq 'server'){$context.Process.StandardInput.WriteLine('stop');$context.Process.StandardInput.Flush()}
                    else{
                        $counts[$context.Role]++
                        if($counts[$context.Role] -le 96){
                            $base=Join-Path $control ($context.Role+'/'+('command-{0:d4}' -f $counts[$context.Role]))
                            Write-New ($base+'.tmp') ((@{id=$counts[$context.Role];action='STOP'}|ConvertTo-Json -Compress)+[Environment]::NewLine)
                            [IO.File]::Move(($base+'.tmp'),($base+'.json'))
                        }
                        $null=$context.Process.CloseMainWindow() # Native close request only; not a fabricated successful shutdown.
                    }
                }catch{Write-Warning ('Owned graceful-close request failed for role '+$context.Role+': '+$_.Exception.GetType().Name)}
            }
        }
        $cleanup=[Diagnostics.Stopwatch]::StartNew()
        while(@($actors.Values|Where-Object{-not $_.Process.HasExited}).Count -gt 0 -and $cleanup.Elapsed.TotalSeconds -lt 45){
            try{Pump-Logs}catch{Write-Warning ('Owned log capture failed during cleanup: '+$_.Exception.GetType().Name);break}
            Start-Sleep -Milliseconds 30
        }
        $remaining=@($actors.Values|Where-Object{-not $_.Process.HasExited}|ForEach-Object{[ordered]@{role=$_.Role;pid=$_.Process.Id;startUtc=$_.StartUtc}})
        Write-New (Join-Path $run 'network-failure.json') (([ordered]@{schemaVersion=1;status='FAILED_OR_INCOMPLETE';mode=$mode;runLabel=$RunLabel;completedReceipts=$steps.Count;remainingOwnedProcesses=$remaining;runtimePassClaimed=$false}|ConvertTo-Json -Depth 6)+[Environment]::NewLine)
        foreach($entry in $remaining){Write-Warning "OWNED_QA_PROCESS_STILL_RUNNING role=$($entry.role) pid=$($entry.pid); do not launch another game/build before inspecting this exact process."}
    }
    foreach($context in @($actors.Values)){$context.OutWriter.Dispose();$context.ErrWriter.Dispose();$context.Process.Dispose()}
}

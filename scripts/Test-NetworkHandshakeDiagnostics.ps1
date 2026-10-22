[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'NetworkHandshake.Diagnostics.ps1')
$script:cases=0
function Check([string]$Label,[scriptblock]$Body){try{& $Body | Out-Null;$script:cases++}catch{throw ('Handshake test failed: '+$Label+'; '+$_.Exception.Message)}}
function Equal($Expected,$Actual){if($Expected -cne $Actual){throw 'Unexpected value.'}}
function Reject([scriptblock]$Body){$failed=$false;try{& $Body | Out-Null}catch{$failed=$true};if(-not $failed){throw 'Expected refusal.'}}
function Observations($Case){
    $client=[pscustomobject]@{schemaVersion=1;status='PASSED';role='a';scenario=$Case.Name;nativeMainThread=$true;threadId=1
        fixturePresent=$Case.ClientFixture;connectionRequested=$true;integratedServer=$false;connected=$Case.Positive;logins=[int]$Case.Positive
        screen=$(if($Case.Positive){'NONE'}else{'net.minecraftforge.client.gui.ModMismatchDisconnectedScreen'})}
    $server=[pscustomobject]@{schemaVersion=1;status='PASSED';role='server';scenario=$Case.Name;nativeMainThread=$true
        dedicatedServer=$true;fixturePresent=$Case.ServerFixture;players=0;logins=[int]$Case.Positive;nativeSends=[int]$Case.Positive;maxPlayers=[int]$Case.Positive}
    foreach($entry in @(@($Case.ClientFixture,$client),@($Case.ServerFixture,$server))){if($entry[0]){
        foreach($field in @('sequence','value','accepted','rejected','lastThreadId')){$entry[1] | Add-Member -NotePropertyName $field -NotePropertyValue 0}
    }}
    if($Case.Positive){$client.sequence=1;$client.value=427;$client.accepted=1;$client.lastThreadId=1}
    return @($client,$server)
}
function Logs($Case,[string]$Role){
    $origin=if($Role -ceq 'a'){'server'}else{'client'}
    $thread=if($Role -ceq 'a'){'Netty Client IO #0'}else{'Netty Server IO #2'}
    $version=if($Case.RejectingRole -ceq $Role){$Case.IncomingVersion}else{'1'}
    $decision=if($Case.RejectingRole -ceq $Role){'REJECTED'}else{'ACCEPTED'}
    $prefix='[07set.2026 10:00:00.001] ['+$thread
    $channel=$Case.Channel
    $result=@($prefix+"/DEBUG] [net.minecraftforge.network.NetworkRegistry/NETREGISTRY]: Channel '$channel' : Version test of '$version' from $origin : $decision")
    if($Case.RejectingRole -ceq $Role){
        $result+=@($prefix+"/ERROR] [net.minecraftforge.network.NetworkRegistry/NETREGISTRY]: Channels [$channel] rejected their $origin side version number")
        $result+=@($prefix+"/ERROR] [net.minecraftforge.network.HandshakeHandler/FMLHANDSHAKE]: Terminating connection with $origin, mismatched mod list")
    }
    return $result
}
foreach($name in @('MATCHED','CLIENT_ABSENT','CLIENT_PROTOCOL','SERVER_ABSENT','SERVER_PROTOCOL')){
    $case=Get-NetworkHandshakeCase $name
    Check ($name+'-contract') { Equal 5242880 $case.RequiredFreeKiB; Equal $name $case.Name; if(-not $case.Positive){Equal $true ($case.ServerFixture -xor $case.ClientFixture)} }
    Check ($name+'-observations') { $o=Observations $case; Assert-NetworkHandshakeObservations $case $o[0] $o[1] }
    Check ($name+'-logs') { $null=Assert-NetworkHandshakeLogs $case (Logs $case 'a') (Logs $case 'server') }
    foreach($field in @('connected','nativeMainThread','fixturePresent','connectionRequested','integratedServer')){
        Check ($name+'-wrong-client-boolean-'+$field) { $o=Observations $case;$o[0].$field=[string]$o[0].$field;Reject {Assert-NetworkHandshakeObservations $case $o[0] $o[1]} }
    }
    foreach($field in @('schemaVersion','logins','threadId')){
        Check ($name+'-wrong-client-counter-'+$field) { $o=Observations $case;$o[0].$field=[string]$o[0].$field;Reject {Assert-NetworkHandshakeObservations $case $o[0] $o[1]} }
    }
    Check ($name+'-entered-server-state') { $o=Observations $case;$o[1].players=1;Reject {Assert-NetworkHandshakeObservations $case $o[0] $o[1]} }
    Check ($name+'-failure-status') { $o=Observations $case;$o[0].status='FAILED';Reject {Assert-NetworkHandshakeObservations $case $o[0] $o[1]} }
    Check ($name+'-wrong-scenario') { $o=Observations $case;$o[1].scenario='UNKNOWN';Reject {Assert-NetworkHandshakeObservations $case $o[0] $o[1]} }
    if($case.Positive){
        foreach($field in @('sequence','value','accepted','lastThreadId')){
            Check ('positive-wrong-'+$field) {$o=Observations $case;$o[0].$field=0;Reject {Assert-NetworkHandshakeObservations $case $o[0] $o[1]}}
        }
        Check 'positive-no-acceptance-logs' {Reject {Assert-NetworkHandshakeLogs $case @() @()}}
    }else{
        Check ($name+'-timeout-is-not-refusal') {$o=Observations $case;$o[0].screen='net.minecraft.client.gui.screens.ConnectScreen';Reject {Assert-NetworkHandshakeObservations $case $o[0] $o[1]}}
        Check ($name+'-invented-absent-counter') {$o=Observations $case;$absent=if($case.ClientFixture){$o[1]}else{$o[0]};$absent|Add-Member -NotePropertyName accepted -NotePropertyValue 0;Reject {Assert-NetworkHandshakeObservations $case $o[0] $o[1]}}
        $goodClient=@(Logs $case 'a');$goodServer=@(Logs $case 'server')
        foreach($mutation in @('missing','bare','duplicate','reorder','wrong-channel','wrong-version','wrong-thread','wrong-logger','wrong-origin','wrong-severity')){
            Check ($name+'-native-log-'+$mutation) {
                $changed=@(Logs $case $case.RejectingRole)
                switch($mutation){
                    'missing' {$changed=@()}
                    'bare' {$changed=@($changed | ForEach-Object {$_ -replace '^.*?\]: ',''})}
                    'duplicate' {$changed+=@($changed[0])}
                    'reorder' {$changed=@($changed[2],$changed[0],$changed[1])}
                    'wrong-channel' {$changed=@($changed | ForEach-Object {$_.Replace('legacybridge_network:snapshot','other:snapshot')})}
                    'wrong-version' {$changed[0]=$changed[0].Replace("Version test of '$($case.IncomingVersion)'","Version test of '9'")}
                    'wrong-thread' {$changed=@($changed | ForEach-Object {$_ -replace 'Netty (Client|Server) IO #[0-9]+','Render thread'})}
                    'wrong-logger' {$changed=@($changed | ForEach-Object {$_.Replace('net.minecraftforge.network.','other.')})}
                    'wrong-origin' {$changed=@($changed | ForEach-Object {$_.Replace('from '+$case.Origin,'from other')})}
                    'wrong-severity' {$changed=@($changed | ForEach-Object {$_.Replace('/ERROR]','/WARN]').Replace('/DEBUG]','/INFO]')})}
                }
                if($case.RejectingRole -ceq 'a'){Reject {Assert-NetworkHandshakeLogs $case $changed $goodServer}}
                else{Reject {Assert-NetworkHandshakeLogs $case $goodClient $changed}}
            }
        }
    }
}
foreach($name in @('', 'matched','MATCHED ','CLIENT_MISSING','ALL')){Check ('unknown-'+$name){Reject {Get-NetworkHandshakeCase $name}}}
Equal 128 $script:cases
Write-Output "NETWORK_HANDSHAKE_DIAGNOSTICS_TESTS_PASSED cases=$script:cases"

# Separate inert filesystem regressions for native orchestration, not network tests.
. (Join-Path $PSScriptRoot 'NetworkHandshake.Control.ps1')
$markerParent=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../build'))
$markerRoot=Join-Path $markerParent ('handshake-marker-tests-'+[Guid]::NewGuid().ToString('N'))
$null=New-Item -ItemType Directory -Path $markerRoot
try{
    foreach($signal in @('finish','stop')){
        $markerPath=Join-Path $markerRoot ($signal+'.request')
        Check ($signal+'-closed-before-publication') {
            Write-NetworkHandshakeMarker $markerPath
            Equal $false (Test-Path -LiteralPath ($markerPath+'.tmp'))
            $reader=[IO.FileStream]::new($markerPath,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None)
            try{Equal 1 $reader.Length;Equal 49 $reader.ReadByte()}finally{$reader.Dispose()}
        }
        Check ($signal+'-existing-marker-preserved') {
            Reject {Write-NetworkHandshakeMarker $markerPath}
            Equal 49 ([IO.File]::ReadAllBytes($markerPath)[0])
            Equal $false (Test-Path -LiteralPath ($markerPath+'.tmp'))
        }
    }
    Check 'unknown-marker-not-published' {
        $unknown=Join-Path $markerRoot 'unknown.request'
        Reject {Write-NetworkHandshakeMarker $unknown}
        Equal $false (Test-Path -LiteralPath $unknown)
        Equal $false (Test-Path -LiteralPath ($unknown+'.tmp'))
    }
    Check 'interrupted-temporary-preserved' {
        $partial=Join-Path $markerRoot 'partial';$null=New-Item -ItemType Directory -Path $partial
        $destination=Join-Path $partial 'finish.request'
        [IO.File]::WriteAllBytes(($destination+'.tmp'),[byte[]]@(50))
        Reject {Write-NetworkHandshakeMarker $destination}
        Equal $false (Test-Path -LiteralPath $destination)
        Equal 50 ([IO.File]::ReadAllBytes(($destination+'.tmp'))[0])
    }
    Equal 134 $script:cases
    Write-Output 'NETWORK_HANDSHAKE_MARKER_TESTS_PASSED cases=6'
}finally{
    $resolved=(Get-Item -LiteralPath $markerRoot -Force).FullName
    if($resolved -cne $markerRoot -or -not $resolved.StartsWith($markerParent+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase) -or
        ([IO.Path]::GetFileName($resolved) -cnotmatch '^handshake-marker-tests-[0-9a-f]{32}$')){throw 'Refuse unsafe test cleanup.'}
    foreach($entry in @(Get-Item -LiteralPath $resolved -Force)+@(Get-ChildItem -LiteralPath $resolved -Force -Recurse)){
        if($entry.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Refuse aliased test cleanup.'}
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}

Check 'normal-mode-does-not-enable-extra-logging' {
    $mode=Get-NetworkHandshakeRunMode $false
    Equal 'DEFAULT' $mode.Name;Equal 'PASSED' $mode.SuccessStatus;Equal $true $mode.NativePassEligible
    Equal 0 @($mode.Arguments).Count
}
Check 'diagnostic-mode-is-not-normal-pass' {
    $mode=Get-NetworkHandshakeRunMode $true
    Equal 'MOJANG_DEBUG' $mode.Name;Equal 'DIAGNOSTIC_ORACLE_PASSED' $mode.SuccessStatus;Equal $false $mode.NativePassEligible
    Equal 1 @($mode.Arguments).Count;Equal '-Dforge.logging.mojang.level=debug' @($mode.Arguments)[0]
}
Check 'packet-markers-require-explicit-debug' {Reject {Get-NetworkHandshakeRunMode $false $true}}
Check 'packet-diagnostics-are-scoped-not-normal-pass' {
    $mode=Get-NetworkHandshakeRunMode $true $true
    Equal 'MOJANG_DEBUG_PACKET_MARKERS' $mode.Name;Equal 'DIAGNOSTIC_ORACLE_PASSED' $mode.SuccessStatus;Equal $false $mode.NativePassEligible
    Equal 2 @($mode.Arguments).Count
    Equal '-Dforge.logging.mojang.level=debug' $mode.Arguments[0]
    Equal '-Dforge.logging.marker.networking=ACCEPT' $mode.Arguments[1]
}
Check 'socket-only-is-diagnostic-without-extra-logging' {
    $mode=Get-NetworkHandshakeRunMode $false $false $true
    Equal 'SOCKET_DEFAULT_LOGGING' $mode.Name;Equal $false $mode.NativePassEligible
    Equal 'DIAGNOSTIC_ORACLE_PASSED' $mode.SuccessStatus
    Equal 1 @($mode.Arguments).Count;Equal '-Dlegacybridge.handshake.socketSnapshot=true' $mode.Arguments[0]
}
Check 'socket-debug-without-packet-markers-is-diagnostic' {
    $mode=Get-NetworkHandshakeRunMode $true $false $true
    Equal 'MOJANG_DEBUG_SOCKET' $mode.Name;Equal $false $mode.NativePassEligible
    Equal 'DIAGNOSTIC_ORACLE_PASSED' $mode.SuccessStatus
    Equal 2 @($mode.Arguments).Count
}
Check 'socket-sample-cannot-promote-to-normal-pass' {
    $mode=Get-NetworkHandshakeRunMode $true $true $true
    Equal 'MOJANG_DEBUG_PACKET_SOCKET' $mode.Name;Equal $false $mode.NativePassEligible
    Equal 'DIAGNOSTIC_ORACLE_PASSED' $mode.SuccessStatus
    Equal 3 @($mode.Arguments).Count;Equal '-Dlegacybridge.handshake.socketSnapshot=true' $mode.Arguments[2]
}
Equal 141 $script:cases
Write-Output 'NETWORK_HANDSHAKE_RUN_MODE_TESTS_PASSED cases=7'
Check 'native-recording-is-not-normal-pass' {
    $mode=Get-NetworkHandshakeRunMode $false $false $false $true
    Equal 'NATIVE_PACKET_RECORDING' $mode.Name;Equal $false $mode.NativePassEligible
    Equal 'DIAGNOSTIC_ORACLE_PASSED' $mode.SuccessStatus;Equal 1 @($mode.Arguments).Count
    Equal '-Dlegacybridge.handshake.packetRecording=true' $mode.Arguments[0]
}
Check 'recording-cannot-combine-with-debug' {Reject {Get-NetworkHandshakeRunMode $true $false $false $true}}
Check 'recording-cannot-combine-with-snapshot' {Reject {Get-NetworkHandshakeRunMode $false $false $true $true}}
Equal 144 $script:cases
Write-Output 'NETWORK_HANDSHAKE_RECORDING_MODE_TESTS_PASSED cases=3'
function RecordingObservation {
    [pscustomobject]@{schemaVersion=1;status='OBSERVED';role='a';scenario='CLIENT_ABSENT';nativeJfr=$true
        payloadPersisted=$false;rawDumpRetained=$false;normalPassClaimed=$false;events=3
        loopbackSocketReads=1;loopbackReadBytes=5;loopbackSocketWrites=0;loopbackWriteBytes=0
        otherSocketEventsCountedNotExported=1
        packetGroups=@([pscustomobject]@{typeProtocolPacket='minecraft.PacketReceived/2/4';count=1;bytes=4})}
}
Check 'bounded-recording-observation' {Assert-NetworkHandshakeRecording (RecordingObservation) 'a' 'CLIENT_ABSENT'}
foreach($mutation in @('status','role','normal-pass','raw-dump','counter-type','group-type','duplicate','unknown-event','count-mismatch','event-bound','group-bound','negative-size','empty')){
    Check ('recording-refusal-'+$mutation) {
        $data=RecordingObservation
        switch($mutation){
            'status' {$data.status='PASSED'}
            'role' {$data.role='server'}
            'normal-pass' {$data.normalPassClaimed=$true}
            'raw-dump' {$data.rawDumpRetained=$true}
            'counter-type' {$data.events='3'}
            'group-type' {$data.packetGroups='not-an-array'}
            'duplicate' {$data.packetGroups+=@($data.packetGroups[0])}
            'unknown-event' {$data.packetGroups[0].typeProtocolPacket='jdk.JVMInformation/2/4'}
            'count-mismatch' {$data.events=4}
            'event-bound' {$data.events=4097}
            'group-bound' {$data.packetGroups[0].bytes=8388609}
            'negative-size' {$data.loopbackReadBytes=-1}
            'empty' {$data.events=0}
        }
        Reject {Assert-NetworkHandshakeRecording $data 'a' 'CLIENT_ABSENT'}
    }
}
Equal 158 $script:cases
Write-Output 'NETWORK_HANDSHAKE_RECORDING_RECEIPT_TESTS_PASSED cases=14'
Check 'platform-control-has-neither-fixture-nor-runtime' {
    $case=Get-NetworkHandshakeCase 'PLATFORM_CLIENT_ABSENT'
    Equal $true $case.PlatformBaseline;Equal $false $case.PublicRequired
    Equal $false $case.ClientFixture;Equal $false $case.ServerFixture
    Equal 'server' $case.RejectingRole;Equal 'legacybridge_handshake_probe:required' $case.Channel
    $o=Observations $case;Assert-NetworkHandshakeObservations $case $o[0] $o[1]
    $null=Assert-NetworkHandshakeLogs $case (Logs $case 'a') (Logs $case 'server')
}
Check 'platform-oracle-does-not-accept-converted-channel' {
    $case=Get-NetworkHandshakeCase 'PLATFORM_CLIENT_ABSENT'
    $wrong=Get-NetworkHandshakeCase 'CLIENT_ABSENT'
    Reject {Assert-NetworkHandshakeLogs $case (Logs $wrong 'a') (Logs $wrong 'server')}
}
Check 'normal-oracle-does-not-accept-platform-channel' {
    $case=Get-NetworkHandshakeCase 'CLIENT_ABSENT'
    $wrong=Get-NetworkHandshakeCase 'PLATFORM_CLIENT_ABSENT'
    Reject {Assert-NetworkHandshakeLogs $case (Logs $wrong 'a') (Logs $wrong 'server')}
}
Check 'platform-modes-never-certify-conversion' {
    foreach($snapshot in @($false,$true)){
        $mode=Get-NetworkHandshakeRunMode $false $false $snapshot $false $true
        Equal $false $mode.NativePassEligible;Equal 'PLATFORM_BASELINE_ORACLE_PASSED' $mode.SuccessStatus
        Equal ([int]$snapshot) @($mode.Arguments).Count
    }
}
Check 'platform-refuses-debug' {Reject {Get-NetworkHandshakeRunMode $true $false $false $false $true}}
Check 'platform-refuses-jfr' {Reject {Get-NetworkHandshakeRunMode $false $false $false $true $true}}
Check 'normal-case-still-requires-runtime' {
    foreach($name in @('MATCHED','CLIENT_ABSENT','CLIENT_PROTOCOL','SERVER_ABSENT','SERVER_PROTOCOL')){
        $case=Get-NetworkHandshakeCase $name;Equal $true $case.PublicRequired;Equal $false $case.PlatformBaseline
        Equal 'legacybridge_network:snapshot' $case.Channel
    }
}
Equal 165 $script:cases
Write-Output 'NETWORK_HANDSHAKE_PLATFORM_CONTROL_TESTS_PASSED cases=7'
Check 'trace-is-client-only-loopback-and-not-normal-pass' {
    $mode=Get-NetworkHandshakeRunMode $false $false $false $false $false $true
    Equal 'READ_INTEREST_TRACE' $mode.Name;Equal $false $mode.NativePassEligible
    Equal 'DIAGNOSTIC_ORACLE_PASSED' $mode.SuccessStatus;Equal 0 @($mode.Arguments).Count
    Equal 1 @($mode.ClientArguments).Count;Equal 262144 $mode.ExtraFreeKiB
    Equal '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:25592' $mode.ClientArguments[0]
}
Check 'normal-mode-does-not-open-debugger-or-raise-memory' {
    $mode=Get-NetworkHandshakeRunMode $false
    Equal 0 @($mode.ClientArguments).Count;Equal 0 $mode.ExtraFreeKiB
}
Check 'trace-refuses-debug' {Reject {Get-NetworkHandshakeRunMode $true $false $false $false $false $true}}
Check 'trace-refuses-snapshot' {Reject {Get-NetworkHandshakeRunMode $false $false $true $false $false $true}}
Check 'trace-refuses-recording' {Reject {Get-NetworkHandshakeRunMode $false $false $false $true $false $true}}
Check 'trace-refuses-platform-control' {Reject {Get-NetworkHandshakeRunMode $false $false $false $false $true $true}}
Equal 171 $script:cases
Write-Output 'NETWORK_HANDSHAKE_READ_TRACE_MODE_TESTS_PASSED cases=6'
function ProtocolTrace([string]$Before='UNSET',[string]$Packet='HANDSHAKING') {
    [pscustomobject]@{schemaVersion=2;records=@([pscustomobject]@{kind='CONFIG_SET';argument=0;phase='BEFORE_RETURN';priorProtocol=$Before;packetProtocol=$Packet;stack=@('net.minecraft.network.Connection#m_129520_:206')})}
}
Check 'send-protocol-null-is-explicit-unset' {Equal 1 (Assert-NetworkHandshakeSendProtocols (ProtocolTrace))}
Check 'send-protocol-known-transition' {Equal 1 (Assert-NetworkHandshakeSendProtocols (ProtocolTrace 'HANDSHAKING' 'LOGIN'))}
Check 'send-protocol-unknown-refuses' {Reject {Assert-NetworkHandshakeSendProtocols (ProtocolTrace 'UNKNOWN' 'LOGIN')}}
Check 'send-protocol-unchanged-refuses' {Reject {Assert-NetworkHandshakeSendProtocols (ProtocolTrace 'LOGIN' 'LOGIN')}}
Check 'send-protocol-unset-packet-refuses' {Reject {Assert-NetworkHandshakeSendProtocols (ProtocolTrace 'LOGIN' 'UNSET')}}
Check 'send-protocol-missing-side-refuses' {$data=ProtocolTrace;$data.records[0].PSObject.Properties.Remove('priorProtocol');Reject {Assert-NetworkHandshakeSendProtocols $data}}
Check 'send-protocol-missing-caller-refuses' {$data=ProtocolTrace;$data.records[0].stack=@();Reject {Assert-NetworkHandshakeSendProtocols $data}}
Check 'send-protocol-enable-refuses' {$data=ProtocolTrace;$data.records[0].argument=1;Reject {Assert-NetworkHandshakeSendProtocols $data}}
Check 'send-protocol-old-schema-not-upgraded' {$data=ProtocolTrace;$data.schemaVersion=1;Reject {Assert-NetworkHandshakeSendProtocols $data}}
Check 'send-protocol-unsampled-not-inferred' {Equal 0 (Assert-NetworkHandshakeSendProtocols ([pscustomobject]@{schemaVersion=2;records=@([pscustomobject]@{kind='SELECTOR_WRITE'})}))}
Equal 181 $script:cases
Write-Output 'NETWORK_HANDSHAKE_SEND_PROTOCOL_TESTS_PASSED cases=10'
function ClearTraceData {
    $record=[pscustomobject]@{ordinal=1;phase='CLEAR_RETURN';bytecodeIndex=15;threadId=1;threadKind='NETTY_CLIENT';nativeConnectionVerified=$true
        autoRead=$true;interestOps=0;readPending=$false;keyValid=$true;inconsistent=$true}
    [pscustomobject]@{schemaVersion=1;reportType='LEGACYBRIDGE_CLEAR_RETURN_TRACE';status='DIAGNOSTIC_WITNESS';witness=$true;eventCount=1
        normalModePassClaimed=$false;executionSuspended=$true;gameDataMutated=$false;targetMethodsInvoked=$false;payloadOrCredentialsRead=$false
        records=@($record);settledObservation=[pscustomobject]@{observed=$true;eventThreadSuspended=$false;millisecondsAfterLastClear=3000
            state=[pscustomobject]@{autoRead=$true;interestOps=0;readPending=$false;keyValid=$true;inconsistent=$true}}}
}
Check 'clear-mode-client-only-not-normal' {
    $mode=Get-NetworkHandshakeRunMode $false $false $false $false $false $false $true
    Equal 'CLEAR_RETURN_TRACE' $mode.Name;Equal $false $mode.NativePassEligible;Equal 'DIAGNOSTIC_ORACLE_PASSED' $mode.SuccessStatus
    Equal 0 @($mode.Arguments).Count;Equal 1 @($mode.ClientArguments).Count;Equal 262144 $mode.ExtraFreeKiB
    Equal '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:25592' $mode.ClientArguments[0]
}
Check 'clear-mode-refuses-all-other-diagnostics' {
    Reject {Get-NetworkHandshakeRunMode $true $false $false $false $false $false $true}
    Reject {Get-NetworkHandshakeRunMode $true $true $false $false $false $false $true}
    Reject {Get-NetworkHandshakeRunMode $false $false $true $false $false $false $true}
    Reject {Get-NetworkHandshakeRunMode $false $false $false $true $false $false $true}
    Reject {Get-NetworkHandshakeRunMode $false $false $false $false $true $false $true}
    Reject {Get-NetworkHandshakeRunMode $false $false $false $false $false $true $true}
}
Check 'prior-read-mode-still-explicit' {Equal 'READ_INTEREST_TRACE' (Get-NetworkHandshakeRunMode $false $false $false $false $false $true).Name}
Check 'clear-persistent-observation' {Assert-NetworkHandshakeClearTrace (ClearTraceData)}
Check 'clear-restored-state-is-not-persistent' {$data=ClearTraceData;$data.settledObservation.state.interestOps=1;$data.settledObservation.state.inconsistent=$false;$data.witness=$false;$data.status='OBSERVED_NO_WITNESS';Assert-NetworkHandshakeClearTrace $data}
Check 'clear-invalid-key-is-not-evidence' {$data=ClearTraceData;$data.records[0].keyValid=$false;$data.records[0].inconsistent=$false;$data.witness=$false;$data.status='OBSERVED_NO_WITNESS';Assert-NetworkHandshakeClearTrace $data}
Check 'clear-misclassified-state-refuses' {$data=ClearTraceData;$data.records[0].inconsistent=$false;Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-early-settled-sample-refuses' {$data=ClearTraceData;$data.settledObservation.millisecondsAfterLastClear=2999;Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-wrong-phase-refuses' {$data=ClearTraceData;$data.records[0].phase='BEFORE_CLEAR';Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-normal-promotion-refuses' {$data=ClearTraceData;$data.normalModePassClaimed=$true;Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-unknown-native-identity-refuses' {$data=ClearTraceData;$data.records[0].nativeConnectionVerified=$false;Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-no-quiet-period-no-invented-state' {$data=ClearTraceData;$data.settledObservation=[pscustomobject]@{observed=$false;eventThreadSuspended=$false;millisecondsAfterLastClear=2;reason='NO_LIVE_QUIET_PERIOD'};$data.witness=$false;$data.status='OBSERVED_NO_WITNESS';Assert-NetworkHandshakeClearTrace $data}
Check 'clear-false-witness-refuses' {$data=ClearTraceData;$data.witness=$false;Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-wrong-schema-refuses' {$data=ClearTraceData;$data.schemaVersion=2;Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-empty-observation-refuses' {$data=ClearTraceData;$data.records=@();Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-oversized-observation-refuses' {$data=ClearTraceData;$data.records=@($data.records[0])*17;Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-wrong-thread-refuses' {$data=ClearTraceData;$data.records[0].threadKind='SERVER_CONNECTOR';Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-invalid-mask-refuses' {$data=ClearTraceData;$data.records[0].interestOps=32;Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-string-schema-refuses' {$data=ClearTraceData;$data.schemaVersion='1';Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-string-native-identity-refuses' {$data=ClearTraceData;$data.records[0].nativeConnectionVerified='True';Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-string-suspension-refuses' {$data=ClearTraceData;$data.settledObservation.eventThreadSuspended='False';Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-string-ordinal-refuses' {$data=ClearTraceData;$data.records[0].ordinal='1';Reject {Assert-NetworkHandshakeClearTrace $data}}
Check 'clear-string-bytecode-index-refuses' {$data=ClearTraceData;$data.records[0].bytecodeIndex='15';Reject {Assert-NetworkHandshakeClearTrace $data}}
Equal 204 $script:cases
Write-Output 'NETWORK_HANDSHAKE_CLEAR_TRACE_TESTS_PASSED cases=23'

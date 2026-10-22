# Pure closed-case contract/oracle. No game launch, original execution or I/O.
function Get-NetworkHandshakeRunMode([bool]$MojangDebug,[bool]$PacketMarkers=$false,[bool]$SocketSnapshot=$false,[bool]$PacketRecording=$false,[bool]$PlatformBaseline=$false,[bool]$ReadInterestTrace=$false,[bool]$ReadClearTrace=$false) {
    # Verified in pinned fmlloader log4j2.xml. DEBUG can activate Mojang debug
    # branches, so these observations cannot silently become a normal-mode PASS.
    if($PacketMarkers -and -not $MojangDebug){throw 'Packet markers require explicit Mojang debug diagnostics.'}
    # The snapshot is independent of logging: inspect a stalled default-logging
    # connection without enabling debug branches/timing. It is still diagnostic.
    if($PacketRecording -and ($MojangDebug -or $PacketMarkers -or $SocketSnapshot)){throw 'Packet recording is an isolated diagnostic; do not combine interventions.'}
    if($PlatformBaseline -and ($MojangDebug -or $PacketMarkers -or $PacketRecording)){throw 'Platform control permits default logging and optional read-only snapshot only.'}
    if($ReadInterestTrace -and ($MojangDebug -or $PacketMarkers -or $SocketSnapshot -or $PacketRecording -or $PlatformBaseline)){throw 'Read-interest trace is a separate client-only diagnostic.'}
    if($ReadClearTrace -and ($MojangDebug -or $PacketMarkers -or $SocketSnapshot -or $PacketRecording -or $PlatformBaseline -or $ReadInterestTrace)){throw 'Clear-return trace must run alone.'}
    $debugger=$ReadInterestTrace -or $ReadClearTrace
    $diagnostic=$MojangDebug -or $SocketSnapshot -or $PacketRecording -or $PlatformBaseline -or $debugger
    $arguments=@()
    $clientArguments=@()
    if($debugger){$clientArguments+=@('-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:25592')}
    if($MojangDebug){$arguments+=@('-Dforge.logging.mojang.level=debug')}
    if($PacketMarkers){$arguments+=@('-Dforge.logging.marker.networking=ACCEPT')}
    if($SocketSnapshot){$arguments+=@('-Dlegacybridge.handshake.socketSnapshot=true')}
    if($PacketRecording){$arguments+=@('-Dlegacybridge.handshake.packetRecording=true')}
    [ordered]@{
        Name=$(if($ReadClearTrace){'CLEAR_RETURN_TRACE'}elseif($ReadInterestTrace){'READ_INTEREST_TRACE'}elseif($PlatformBaseline){if($SocketSnapshot){'PLATFORM_BASELINE_SOCKET'}else{'PLATFORM_BASELINE'}}elseif($PacketRecording){'NATIVE_PACKET_RECORDING'}elseif($SocketSnapshot -and $PacketMarkers){'MOJANG_DEBUG_PACKET_SOCKET'}elseif($SocketSnapshot -and $MojangDebug){'MOJANG_DEBUG_SOCKET'}elseif($SocketSnapshot){'SOCKET_DEFAULT_LOGGING'}elseif($PacketMarkers){'MOJANG_DEBUG_PACKET_MARKERS'}elseif($MojangDebug){'MOJANG_DEBUG'}else{'DEFAULT'})
        SuccessStatus=$(if($PlatformBaseline){'PLATFORM_BASELINE_ORACLE_PASSED'}elseif($diagnostic){'DIAGNOSTIC_ORACLE_PASSED'}else{'PASSED'})
        NativePassEligible=(-not $diagnostic)
        Arguments=$arguments
        ClientArguments=$clientArguments
        ExtraFreeKiB=$(if($debugger){262144}else{0})
    }
}
function Assert-NetworkHandshakeClearState($State) {
    foreach($name in @('autoRead','readPending','keyValid','inconsistent')){if($State.$name -isnot [bool]){throw 'Invalid clear state boolean'}}
    if(($State.interestOps -isnot [int] -and $State.interestOps -isnot [long]) -or $State.interestOps -lt 0 -or $State.interestOps -gt 31){throw 'Invalid selector mask'}
    $inconsistent=$State.keyValid -and $State.autoRead -and (($State.interestOps -band 1) -eq 0) -and -not $State.readPending
    if($State.inconsistent -ne $inconsistent){throw 'Clear state classification differs'}
    return $inconsistent
}
function Assert-NetworkHandshakeClearTrace($Data) {
    if(($Data.schemaVersion -isnot [int] -and $Data.schemaVersion -isnot [long]) -or
        $Data.schemaVersion -ne 1 -or $Data.reportType -cne 'LEGACYBRIDGE_CLEAR_RETURN_TRACE' -or
        $Data.status -cnotin @('DIAGNOSTIC_WITNESS','OBSERVED_NO_WITNESS') -or $Data.witness -isnot [bool] -or
        $Data.records -isnot [array] -or $Data.records.Count -lt 1 -or $Data.records.Count -gt 16 -or
        ($Data.eventCount -isnot [int] -and $Data.eventCount -isnot [long]) -or $Data.eventCount -lt $Data.records.Count -or $Data.eventCount -gt 4096){throw 'Invalid clear report envelope'}
    foreach($name in @('normalModePassClaimed','executionSuspended','gameDataMutated','targetMethodsInvoked','payloadOrCredentialsRead')){
        if($Data.$name -isnot [bool] -or $Data.$name -ne ($name -ceq 'executionSuspended')){throw 'Clear diagnostic boundary differs'}
    }
    $candidate=$false;$ordinal=0
    foreach($record in $Data.records){
        $ordinal++
        if(($record.ordinal -isnot [int] -and $record.ordinal -isnot [long]) -or $record.ordinal -ne $ordinal -or
            $record.phase -cne 'CLEAR_RETURN' -or ($record.bytecodeIndex -isnot [int] -and $record.bytecodeIndex -isnot [long]) -or
            $record.bytecodeIndex -ne 15 -or $record.nativeConnectionVerified -isnot [bool] -or
            $record.nativeConnectionVerified -cne $true -or $record.threadKind -cne 'NETTY_CLIENT' -or
            ($record.threadId -isnot [int] -and $record.threadId -isnot [long]) -or $record.threadId -le 0){throw 'Invalid clear record identity'}
        $candidate=(Assert-NetworkHandshakeClearState $record) -or $candidate
    }
    $later=$Data.settledObservation
    if($later.observed -isnot [bool] -or $later.eventThreadSuspended -isnot [bool] -or $later.eventThreadSuspended -cne $false -or
        ($later.millisecondsAfterLastClear -isnot [int] -and $later.millisecondsAfterLastClear -isnot [long]) -or $later.millisecondsAfterLastClear -lt 0){throw 'Invalid settled observation'}
    $persistent=$false
    if($later.observed){
        if($later.millisecondsAfterLastClear -lt 3000){throw 'Missing quiet period'}
        $persistent=(Assert-NetworkHandshakeClearState $later.state) -and $candidate
    }elseif($later.reason -cne 'NO_LIVE_QUIET_PERIOD' -or $null -ne $later.PSObject.Properties['state']){throw 'Invented settled state'}
    if($Data.witness -ne $persistent -or ($Data.status -ceq 'DIAGNOSTIC_WITNESS') -ne $persistent){throw 'Clear witness claim differs'}
}
function Assert-NetworkHandshakeSendProtocols($Data) {
    if($Data.schemaVersion -ne 2){throw 'Protocol observation requires trace schema 2.'}
    $count=0
    foreach($record in $Data.records){
        $hasPrior=$null -ne $record.PSObject.Properties['priorProtocol']
        $hasPacket=$null -ne $record.PSObject.Properties['packetProtocol']
        if($hasPrior -ne $hasPacket){throw 'Incomplete protocol observation.'}
        if(-not $hasPrior){continue}
        if($record.kind -cne 'CONFIG_SET' -or $record.argument -ne 0 -or $record.phase -cne 'BEFORE_RETURN' -or
            $record.priorProtocol -cnotin @('UNSET','HANDSHAKING','PLAY','STATUS','LOGIN') -or
            $record.packetProtocol -cnotin @('HANDSHAKING','PLAY','STATUS','LOGIN') -or
            $record.priorProtocol -ceq $record.packetProtocol -or
            @($record.stack | Where-Object{$_ -cmatch '^net\.minecraft\.network\.Connection#m_129520_:206$'}).Count -ne 1){throw 'Invalid send protocol observation.'}
        $count++
    }
    return $count # Zero means not sampled, never an inferred protocol or successful gate.
}
function Get-NetworkHandshakeCase([string]$Name) {
    if($Name -cnotin @('MATCHED','CLIENT_ABSENT','CLIENT_PROTOCOL','SERVER_ABSENT','SERVER_PROTOCOL','PLATFORM_CLIENT_ABSENT')){throw 'Unknown handshake case.'}
    $baseline=$Name -ceq 'PLATFORM_CLIENT_ABSENT'
    $positive=$Name -ceq 'MATCHED'
    $badServer=$Name.StartsWith('SERVER_',[StringComparison]::Ordinal)
    $protocol=$Name.EndsWith('_PROTOCOL',[StringComparison]::Ordinal)
    [ordered]@{
        Name=$Name;Positive=$positive;ServerFixture=(-not $baseline -and ($positive -or -not $badServer));ClientFixture=($positive -or $badServer)
        PlatformBaseline=$baseline;PublicRequired=(-not $baseline)
        Channel=$(if($baseline){'legacybridge_handshake_probe:required'}else{'legacybridge_network:snapshot'})
        RejectingRole=$(if($positive){'NONE'}elseif($badServer){'a'}else{'server'})
        Origin=$(if($badServer){'server'}else{'client'})
        IncomingVersion=$(if($positive){'1'}elseif($protocol){'2'}else{'ABSENT '+[char]::ConvertFromUtf32(0x1F914)})
        RequiredFreeKiB=5242880 # two native processes only; does not lower the three-process 6 GiB gate
    }
}
function Assert-NetworkHandshakeRecording($Data,[string]$Role,[string]$Scenario) {
    if($Role -cnotin @('a','server') -or $Data.schemaVersion -ne 1 -or $Data.status -cne 'OBSERVED' -or
       $Data.role -cne $Role -or $Data.scenario -cne $Scenario){throw 'Wrong recording identity/schema/status.'}
    foreach($field in @('nativeJfr','payloadPersisted','rawDumpRetained','normalPassClaimed')){
        if($Data.$field -isnot [bool] -or $Data.$field -ne ($field -ceq 'nativeJfr')){throw 'Recording boolean contract differs.'}
    }
    foreach($field in @('events','loopbackSocketReads','loopbackReadBytes','loopbackSocketWrites','loopbackWriteBytes','otherSocketEventsCountedNotExported')){
        if(($Data.$field -isnot [int] -and $Data.$field -isnot [long]) -or $Data.$field -lt 0){throw 'Invalid recording counter.'}
    }
    if($Data.events -le 0 -or $Data.events -gt 4096 -or $Data.packetGroups -isnot [array] -or $Data.packetGroups.Count -gt 64){throw 'Empty or oversized recording envelope.'}
    $groups=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $count=[long]$Data.loopbackSocketReads+$Data.loopbackSocketWrites+$Data.otherSocketEventsCountedNotExported
    foreach($group in $Data.packetGroups){
        if($group.typeProtocolPacket -isnot [string] -or $group.typeProtocolPacket -cnotmatch '^minecraft\.Packet(Sent|Received)/(-1|0|1|2)/(0|[1-9][0-9]{0,2})$' -or
            [int]($group.typeProtocolPacket.Split('/')[-1]) -gt 255 -or -not $groups.Add($group.typeProtocolPacket)){throw 'Invalid/duplicate packet metadata group.'}
        foreach($field in @('count','bytes')){if(($group.$field -isnot [int] -and $group.$field -isnot [long]) -or $group.$field -lt 0){throw 'Invalid packet metadata counter.'}}
        if($group.count -le 0 -or $group.count -gt 4096 -or $group.bytes -gt (8388608L*$group.count)){throw 'Packet metadata exceeds bounds.'}
        $count+=$group.count
    }
    if($count -ne $Data.events -or ($Data.loopbackSocketReads -eq 0 -and $Data.loopbackReadBytes -ne 0) -or
        ($Data.loopbackSocketWrites -eq 0 -and $Data.loopbackWriteBytes -ne 0)){throw 'Recording counters do not reconcile.'}
}
function Assert-NetworkHandshakeObservations($Case, $Client, $Server) {
    foreach($entry in @(@($Client,@('nativeMainThread','fixturePresent','connectionRequested','integratedServer','connected'),@('schemaVersion','logins','threadId')),
                       @($Server,@('nativeMainThread','fixturePresent','dedicatedServer'),@('schemaVersion','players','maxPlayers','logins','nativeSends')))){
        $data=$entry[0]
        foreach($field in $entry[1]){if($data.$field -isnot [bool]){throw 'Native boolean field has wrong type or is missing.'}}
        $numbers=@($entry[2]); if($data.fixturePresent){$numbers+=@('sequence','value','accepted','rejected','lastThreadId')}
        foreach($field in $numbers){if(($data.$field -isnot [int] -and $data.$field -isnot [long]) -or $data.$field -lt 0){throw 'Native counter field has wrong type, is missing or is negative.'}}
    }
    foreach($entry in @(@('a',$Client),@('server',$Server))){
        $data=$entry[1]
        if($data.schemaVersion -ne 1 -or $data.status -cne 'PASSED' -or $data.role -cne $entry[0] -or
            $data.scenario -cne $Case.Name -or $data.nativeMainThread -cne $true){throw 'Invalid native handshake observation identity/thread/status.'}
    }
    if($Client.fixturePresent -cne $Case.ClientFixture -or $Server.fixturePresent -cne $Case.ServerFixture -or
        $Client.connectionRequested -cne $true -or $Client.integratedServer -cne $false -or
        $Server.dedicatedServer -cne $true -or $Server.players -ne 0){throw 'Native inventory/world/connection state differs.'}
    if($Case.Positive){
        if($Client.connected -cne $true -or $Client.logins -ne 1 -or $Client.sequence -ne 1 -or $Client.value -ne 427 -or
            $Client.accepted -ne 1 -or $Client.rejected -ne 0 -or $Client.threadId -le 0 -or $Client.lastThreadId -ne $Client.threadId -or
            $Server.logins -ne 1 -or $Server.nativeSends -ne 1 -or $Server.maxPlayers -ne 1){throw 'Positive native control did not deliver exactly one snapshot on the client thread.'}
    }else{
        if($Client.connected -cne $false -or $Client.logins -ne 0 -or $Server.logins -ne 0 -or $Server.nativeSends -ne 0 -or $Server.maxPlayers -ne 0 -or
            $Client.screen -cnotin @('net.minecraft.client.gui.screens.DisconnectedScreen','net.minecraftforge.client.gui.ModMismatchDisconnectedScreen')){throw 'Negative peer was not rejected before play.'}
        if($Case.ClientFixture){foreach($field in @('sequence','value','accepted','rejected','lastThreadId')){if($Client.$field -ne 0){throw 'Rejected client fixture state changed.'}}}
    }
    if($Case.ServerFixture){foreach($field in @('sequence','value','accepted','rejected','lastThreadId')){if($Server.$field -ne 0){throw 'Server S2C fixture state changed.'}}}
    foreach($entry in @(@($Case.ClientFixture,$Client),@($Case.ServerFixture,$Server))){
        if(-not $entry[0]){foreach($field in @('sequence','value','accepted','rejected','lastThreadId')){
            if($null -ne $entry[1].PSObject.Properties[$field]){throw 'Missing fixture cannot be represented by invented counters.'}
        }}
    }
}
function Assert-NetworkHandshakeLogs($Case, [string[]]$ClientLines, [string[]]$ServerLines) {
    $counts=@{}
    $timestamp='\[[^\]\r\n]{1,64}\] '
    $channel=$Case.Channel
    if($channel -cne $(if($Case.PlatformBaseline){'legacybridge_handshake_probe:required'}else{'legacybridge_network:snapshot'})){throw 'Closed native channel differs from selected case.'}
    foreach($role in @('a','server')){
        $lines=if($role -ceq 'a'){$ClientLines}else{$ServerLines}
        $thread=if($role -ceq 'a'){'Netty Client IO #[0-9]+'}else{'Netty Server IO #[0-9]+'}
        $origin=if($role -ceq 'a'){'server'}else{'client'}
        $version=if($role -ceq $Case.RejectingRole){$Case.IncomingVersion}else{'1'}
        $decision=if($role -ceq $Case.RejectingRole){'REJECTED'}else{'ACCEPTED'}
        $versionText="Channel '$channel' : Version test of '$version' from $origin : $decision"
        $versionPattern='\A'+$timestamp+'\['+$thread+'/DEBUG\] \[net\.minecraftforge\.network\.NetworkRegistry/NETREGISTRY\]: '+[regex]::Escape($versionText)+'\r?\z'
        $errorPattern='\A'+$timestamp+'\['+$thread+'/ERROR\] \[net\.minecraftforge\.network\.NetworkRegistry/NETREGISTRY\]: '+
            [regex]::Escape("Channels [$channel] rejected their $origin side version number")+'\r?\z'
        $terminatePattern='\A'+$timestamp+'\['+$thread+'/ERROR\] \[net\.minecraftforge\.network\.HandshakeHandler/FMLHANDSHAKE\]: '+
            [regex]::Escape("Terminating connection with $origin, mismatched mod list")+'\r?\z'
        $versionCount=0; $errorCount=0; $terminationCount=0; $rejectStage=0
        foreach($line in $lines){
            if([regex]::IsMatch($line,$versionPattern)){
                if($role -ceq $Case.RejectingRole){if($rejectStage -ne 0){throw 'Repeated/out-of-order rejection version test.'};$rejectStage=1}
                $versionCount++
            }
            if([regex]::IsMatch($line,$errorPattern)){
                if($role -ceq $Case.RejectingRole){if($rejectStage -ne 1){throw 'Out-of-order native channel rejection.'};$rejectStage=2}
                $errorCount++
            }
            if([regex]::IsMatch($line,$terminatePattern)){
                if($role -ceq $Case.RejectingRole){if($rejectStage -ne 2){throw 'Out-of-order native termination.'};$rejectStage=3}
                $terminationCount++
            }
            if($Case.Positive -and $line -cmatch 'rejected their (client|server) side version number|mismatched mod list'){throw 'Matched peer had a native mismatch.'}
        }
        if($role -ceq $Case.RejectingRole){
            if($versionCount -ne 1 -or $errorCount -ne 1 -or $terminationCount -ne 1){throw 'Exact native version/channel/termination oracle did not match once on the rejecting side.'}
        }elseif($Case.Positive -and $versionCount -ne 1){throw 'Positive peer channel acceptance evidence missing or repeated.'}
        $counts[$role]=[ordered]@{versionTests=$versionCount;channelRejections=$errorCount;handshakeTerminations=$terminationCount}
    }
    return $counts
}

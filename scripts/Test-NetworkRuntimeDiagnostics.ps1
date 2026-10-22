# Pure, game-free tests for the closed native diagnostic oracle. No Pester or disk output.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'NetworkRuntime.Diagnostics.ps1')
$script:DiagnosticTestCaseCount = 0
$script:DiagnosticTestAssertionCount = 0

function New-DiagnosticObservation([string]$Kind, [string]$Role, [bool]$UnknownWindow, [bool]$DirectionWindow) {
    $values = @{
        Role = $Role
        UnknownIdWindow = $UnknownWindow
        WrongDirectionWindow = $DirectionWindow
        NativeUnknownIdCount = 0
        NativeUnknownIdNettyCount = 0
        NativeUnknownIdRenderCount = 0
        NativeUnknownFallbackCount = 0
        NativeWrongDirectionCount = 0
    }
    if ($Kind -eq 'hashtable') { return $values }
    return [pscustomobject]$values
}

function Assert-DiagnosticEqual([object]$Expected, [object]$Actual, [string]$Context) {
    $script:DiagnosticTestAssertionCount++
    if ($Expected -cne $Actual) {
        throw ('Assertion failed: ' + $Context + '; expected=' + $Expected + '; actual=' + $Actual)
    }
}

function Assert-DiagnosticRejected([scriptblock]$Action, [string]$Context) {
    $script:DiagnosticTestAssertionCount++
    $rejected = $false
    try { $null = & $Action } catch { $rejected = $true }
    if (-not $rejected) { throw ('Expected diagnostic refusal: ' + $Context) }
}

function Invoke-DiagnosticCase([string]$Name, [scriptblock]$Action) {
    try { $null = & $Action } catch {
        throw ('Diagnostic case failed: ' + $Name + '; ' + $_.Exception.Message)
    }
    $script:DiagnosticTestCaseCount++
}

$unknown = 'Received invalid discriminator byte 255 on channel legacybridge_network:snapshot'
$wrongDirection = 'LegacyNetA lost connection: Illegal packet received, terminating connection'
$fallback = 'Unknown custom packet identifier: legacybridge_network:snapshot'
$nettyLine = '[12:34:56] [Netty Client IO #0/ERROR] [ne.mi.ne.si.IndexedMessageCodec/SIMPLENET]: ' + $unknown
$renderLine = '[12:34:56] [Render thread/ERROR] [ne.mi.ne.si.IndexedMessageCodec/SIMPLENET]: ' + $unknown
$fallbackLine = '[12:34:56] [Render thread/WARN] [minecraft/ClientPacketListener]: ' + $fallback
$orderedLines = @($nettyLine, $renderLine, $fallbackLine)
$unknownSequences = @(
    @{ lines = $orderedLines },
    @{ lines = @(($nettyLine + [char]13), ($renderLine + [char]13), ($fallbackLine + [char]13)) },
    @{ lines = @(
        ('[07set.2026 03:17:38.208] [Netty Client IO #0/ERROR] [net.minecraftforge.network.simple.IndexedMessageCodec/SIMPLENET]: ' + $unknown),
        ('[07set.2026 03:17:38.240] [Render thread/ERROR] [net.minecraftforge.network.simple.IndexedMessageCodec/SIMPLENET]: ' + $unknown),
        ('[07set.2026 03:17:38.240] [Render thread/WARN] [net.minecraft.client.multiplayer.ClientPacketListener/]: ' + $fallback)
    ) },
    @{ lines = @(
        ('[Netty Client IO #1/ERROR] [ne.mi.ne.si.IndexedMessageCodec/SIMPLENET]: ' + $unknown),
        ('[Render thread/ERROR] [ne.mi.ne.si.IndexedMessageCodec/SIMPLENET]: ' + $unknown),
        ('[Render thread/WARN] [minecraft/ClientPacketListener]: ' + $fallback)
    ) },
    @{ lines = @($nettyLine.Replace('#0/', '#17/'), $renderLine, $fallbackLine) }
)
$directionForms = @(
    $wrongDirection,
    ('[12:34:56] [Server thread/INFO] [minecraft/ServerGamePacketListenerImpl]: ' + $wrongDirection),
    ($wrongDirection + [char]13),
    ('[12:34:56] [Server thread/INFO] [minecraft/ServerGamePacketListenerImpl]: ' + $wrongDirection + [char]13)
)
$unrelated = @(
    '',
    'Received invalid discriminator byte 25 on channel legacybridge_network:snapshot',
    'Received invalid discriminator byte 2550 on channel legacybridge_network:snapshot',
    'Received invalid discriminator byte 0255 on channel legacybridge_network:snapshot',
    'Received invalid discriminator byte 255 on channel other_mod:snapshot',
    'Received invalid discriminator byte 255 on channel legacybridge_network:snapshot_extra',
    'Received invalid discriminator byte 255 on channel legacybridge_network:prefix_snapshot',
    'Received invalid discriminator byte 255 on channel prefix_legacybridge_network:snapshot',
    'Received invalid discriminator byte 255 on channel legacybridge_network:snapshot suffix',
    'Received invalid discriminator byte 255 on channel legacybridge_network:Snapshot',
    'received invalid discriminator byte 255 on channel legacybridge_network:snapshot',
    'Received Invalid discriminator byte 255 on channel legacybridge_network:snapshot',
    'NotReceived invalid discriminator byte 255 on channel legacybridge_network:snapshot',
    'Unknown discriminator on unrelated channel',
    'Undefined message',
    'LegacyNetB lost connection: Illegal packet received, terminating connection',
    'LegacyNetA2 lost connection: Illegal packet received, terminating connection',
    'LegacyNeta lost connection: Illegal packet received, terminating connection',
    'NotLegacyNetA lost connection: Illegal packet received, terminating connection',
    'LegacyNetA lost connection: illegal packet received, terminating connection',
    'LegacyNetA lost connection: Illegal packet received, terminating connection suffix',
    'LegacyNetA lost connection: Disconnected',
    'Invalid message direction',
    'NETWORK_INVALID_PAYLOAD',
    '[Render thread/INFO]: NETWORK_OUT_OF_ORDER',
    'NETWORK_SESSION_MUST_START_AT_ONE'
)
$unrelated += @(
    'Unknown custom packet identifier: legacybridge_network:snapshot_extra',
    'Unknown custom packet identifier: other_mod:snapshot',
    'Unknown custom packet identifier: legacybridge_network:Snapshot',
    'Unknown custom packet identifier: legacybridge_network:snapshot suffix',
    'unknown custom packet identifier: legacybridge_network:snapshot',
    'NotUnknown custom packet identifier: legacybridge_network:snapshot'
)
$badPrefixes = @(
    $unknown,
    ($unknown + [char]13),
    ('[12:34:56] ' + $unknown),
    ('[Netty Client IO #0/ERROR]: ' + $unknown),
    $nettyLine.Replace('/ERROR]', '/WARN]'),
    $nettyLine.Replace('/ERROR]', '/error]'),
    $nettyLine.Replace('Netty Client IO #0', 'Server thread'),
    $nettyLine.Replace('Netty Client IO #0', 'Netty Server IO #0'),
    $nettyLine.Replace('Netty Client IO #0', 'Netty Client IO #x'),
    $nettyLine.Replace('Netty Client IO #0', 'Netty Client IO #-1'),
    $nettyLine.Replace('Netty Client IO #0', 'netty Client IO #0'),
    $nettyLine.Replace('IndexedMessageCodec', 'OtherCodec'),
    $nettyLine.Replace('SIMPLENET', 'OTHER'),
    ('prefix ' + $nettyLine),
    $renderLine.Replace('Render thread', 'Render thread #0'),
    $renderLine.Replace('/ERROR]', '/INFO]'),
    $renderLine.Replace('IndexedMessageCodec', 'IndexedMessageCodecExtra'),
    $fallback,
    ($fallback + [char]13),
    ('[12:34:56] ' + $fallback),
    ('[Render thread/WARN]: ' + $fallback),
    $fallbackLine.Replace('/WARN]', '/ERROR]'),
    $fallbackLine.Replace('/WARN]', '/warn]'),
    $fallbackLine.Replace('Render thread', 'Netty Client IO #0'),
    $fallbackLine.Replace('Render thread', 'Server thread'),
    $fallbackLine.Replace('minecraft/ClientPacketListener', 'minecraft/OtherListener'),
    $fallbackLine.Replace('minecraft/ClientPacketListener', 'minecraft/ClientPacketListenerExtra'),
    ('prefix ' + $fallbackLine)
)
$badOrderings = @(
    @{ before = @(); rejected = $renderLine },
    @{ before = @(); rejected = $fallbackLine },
    @{ before = @($nettyLine); rejected = $fallbackLine },
    @{ before = @($nettyLine); rejected = $nettyLine },
    @{ before = @($nettyLine, $renderLine); rejected = $nettyLine },
    @{ before = @($nettyLine, $renderLine); rejected = $renderLine },
    @{ before = $orderedLines; rejected = $nettyLine },
    @{ before = $orderedLines; rejected = $renderLine },
    @{ before = $orderedLines; rejected = $fallbackLine }
)

function Get-DiagnosticCounts($Observation) {
    return [string]::Join(',', @(
        $Observation.NativeUnknownIdCount, $Observation.NativeUnknownIdNettyCount,
        $Observation.NativeUnknownIdRenderCount, $Observation.NativeUnknownFallbackCount,
        $Observation.NativeWrongDirectionCount
    ))
}

foreach ($kind in @('pscustomobject', 'hashtable')) {
    for ($index = 0; $index -lt $unknownSequences.Count; $index++) {
        Invoke-DiagnosticCase ($kind + '-unknown-positive-' + $index) {
            $observation = New-DiagnosticObservation $kind 'a' $true $false
            $lines = $unknownSequences[$index].lines
            Assert-DiagnosticEqual 3 @($lines).Count 'Positive fixture includes the complete native chain'
            Update-NetworkDiagnosticObservation $observation $lines[0]
            Assert-DiagnosticEqual '1,1,0,0,0' (Get-DiagnosticCounts $observation) 'First exact ERROR is the Netty event'
            Update-NetworkDiagnosticObservation $observation $lines[1]
            Assert-DiagnosticEqual '2,1,1,0,0' (Get-DiagnosticCounts $observation) 'Second exact ERROR is the main-thread retry, not another payload'
            Update-NetworkDiagnosticObservation $observation $lines[2]
            Assert-DiagnosticEqual '2,1,1,1,0' (Get-DiagnosticCounts $observation) 'Exact WARN completes the vanilla fallback once'
        }
    }
    for ($index = 0; $index -lt $directionForms.Count; $index++) {
        Invoke-DiagnosticCase ($kind + '-direction-positive-' + $index) {
            $observation = New-DiagnosticObservation $kind 'server' $false $true
            Update-NetworkDiagnosticObservation $observation $directionForms[$index]
            Assert-DiagnosticEqual '0,0,0,0,1' (Get-DiagnosticCounts $observation) 'Wrong direction remains independent and counted once'
        }
    }
    for ($index = 0; $index -lt $unrelated.Count; $index++) {
        Invoke-DiagnosticCase ($kind + '-unrelated-' + $index) {
            # Exercise both roles; unrelated native/fixture diagnostics never authorize a PASS.
            foreach ($role in @('a', 'server')) {
                $observation = New-DiagnosticObservation $kind $role $true $true
                Update-NetworkDiagnosticObservation $observation $unrelated[$index]
                Assert-DiagnosticEqual '0,0,0,0,0' (Get-DiagnosticCounts $observation) 'Unrelated line leaves every diagnostic count unchanged'
            }
        }
    }
    for ($index = 0; $index -lt $badPrefixes.Count; $index++) {
        Invoke-DiagnosticCase ($kind + '-unknown-unrecognized-prefix-' + $index) {
            $observation = New-DiagnosticObservation $kind 'a' $true $false
            Assert-DiagnosticRejected { Update-NetworkDiagnosticObservation $observation $badPrefixes[$index] } 'Exact terminal without proven severity/logger/thread cannot be guessed'
            Assert-DiagnosticEqual '0,0,0,0,0' (Get-DiagnosticCounts $observation) 'A malformed prefix does not modify state'
        }
    }
    for ($index = 0; $index -lt $badOrderings.Count; $index++) {
        Invoke-DiagnosticCase ($kind + '-unknown-order-or-repeat-' + $index) {
            $observation = New-DiagnosticObservation $kind 'a' $true $false
            foreach ($line in $badOrderings[$index].before) { Update-NetworkDiagnosticObservation $observation $line }
            $before = Get-DiagnosticCounts $observation
            Assert-DiagnosticRejected { Update-NetworkDiagnosticObservation $observation $badOrderings[$index].rejected } 'One ordered native chain is mandatory; duplicate or third codec event refuses'
            Assert-DiagnosticEqual $before (Get-DiagnosticCounts $observation) 'Rejected ordering does not mutate the accumulated evidence'
        }
    }
    for ($stage = 0; $stage -lt $orderedLines.Count; $stage++) {
        foreach ($role in @('server', 'b')) {
            Invoke-DiagnosticCase ($kind + '-unknown-stage-' + $stage + '-wrong-role-' + $role) {
                $observation = New-DiagnosticObservation $kind 'a' $true $true
                for ($prior = 0; $prior -lt $stage; $prior++) { Update-NetworkDiagnosticObservation $observation $orderedLines[$prior] }
                $before = Get-DiagnosticCounts $observation
                $observation.Role = $role
                Assert-DiagnosticRejected { Update-NetworkDiagnosticObservation $observation $orderedLines[$stage] } 'Every stage requires client A, including fallback'
                Assert-DiagnosticEqual $before (Get-DiagnosticCounts $observation) 'Wrong-role refusal leaves counters unchanged'
            }
        }
        Invoke-DiagnosticCase ($kind + '-unknown-stage-' + $stage + '-closed-window') {
            $observation = New-DiagnosticObservation $kind 'a' $true $true
            for ($prior = 0; $prior -lt $stage; $prior++) { Update-NetworkDiagnosticObservation $observation $orderedLines[$prior] }
            $before = Get-DiagnosticCounts $observation
            $observation.UnknownIdWindow = $false
            Assert-DiagnosticRejected { Update-NetworkDiagnosticObservation $observation $orderedLines[$stage] } 'Another open window cannot authorize any unknown-ID stage'
            Assert-DiagnosticEqual $before (Get-DiagnosticCounts $observation) 'A closed window cannot alter counters'
        }
    }
    foreach ($role in @('a', 'b')) {
        Invoke-DiagnosticCase ($kind + '-direction-wrong-role-' + $role) {
            $observation = New-DiagnosticObservation $kind $role $true $true
            Assert-DiagnosticRejected { Update-NetworkDiagnosticObservation $observation $wrongDirection } 'Direction diagnostic on wrong process'
        }
    }
    Invoke-DiagnosticCase ($kind + '-direction-before-window') {
        $observation = New-DiagnosticObservation $kind 'server' $true $false
        Assert-DiagnosticRejected { Update-NetworkDiagnosticObservation $observation $wrongDirection } 'Another open window cannot authorize wrong direction'
    }
    Invoke-DiagnosticCase ($kind + '-direction-duplicate') {
        $observation = New-DiagnosticObservation $kind 'server' $false $true
        Update-NetworkDiagnosticObservation $observation $wrongDirection
        Assert-DiagnosticEqual 1 $observation.NativeWrongDirectionCount 'First direction observation approved'
        Assert-DiagnosticRejected { Update-NetworkDiagnosticObservation $observation $directionForms[1] } 'Repeated direction diagnostic rejected'
    }
    Invoke-DiagnosticCase ($kind + '-direction-after-closed-window') {
        $observation = New-DiagnosticObservation $kind 'server' $false $true
        Update-NetworkDiagnosticObservation $observation $wrongDirection
        $observation.WrongDirectionWindow = $false
        Assert-DiagnosticRejected { Update-NetworkDiagnosticObservation $observation $wrongDirection } 'Closed direction window remains closed'
    }
    Invoke-DiagnosticCase ($kind + '-unrelated-after-observation') {
        $client = New-DiagnosticObservation $kind 'a' $true $false
        $server = New-DiagnosticObservation $kind 'server' $false $true
        foreach ($line in $orderedLines) { Update-NetworkDiagnosticObservation $client $line }
        Update-NetworkDiagnosticObservation $server $wrongDirection
        foreach ($line in $unrelated) {
            Update-NetworkDiagnosticObservation $client $line
            Update-NetworkDiagnosticObservation $server $line
        }
        Assert-DiagnosticEqual '2,1,1,1,0' (Get-DiagnosticCounts $client) 'Noise preserves the complete ordered client chain'
        Assert-DiagnosticEqual '0,0,0,0,1' (Get-DiagnosticCounts $server) 'Noise preserves only the server direction observation'
    }
}

Assert-DiagnosticEqual 186 $script:DiagnosticTestCaseCount 'Every declared diagnostic case executed'
Write-Output ('NETWORK_RUNTIME_DIAGNOSTICS_TESTS_PASSED cases=' + $script:DiagnosticTestCaseCount + ' assertions=' + $script:DiagnosticTestAssertionCount)

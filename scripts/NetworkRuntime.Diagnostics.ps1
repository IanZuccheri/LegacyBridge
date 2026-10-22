# Pure QA log oracle for pinned Forge 47.4.23, never packaged in LegacyBridge.jar.
# One unknown payload traverses Netty, then the main-thread retry, then vanilla fallback.
# Count the exact ordered 2 ERROR + 1 WARN native chain, not an arbitrary duplicate allowance.
# Exact player-disconnect behavior is independent. No I/O/processes.
function Update-NetworkDiagnosticObservation($Observation, [AllowEmptyString()][string]$Line) {
    $regexOptions = [Text.RegularExpressions.RegexOptions]::CultureInvariant
    $unknownText = 'Received invalid discriminator byte 255 on channel legacybridge_network:snapshot'
    $fallbackText = 'Unknown custom packet identifier: legacybridge_network:snapshot'
    $unknown = [regex]::IsMatch($Line,
        '(?:^|\s)' + [regex]::Escape($unknownText) + '\r?\z', $regexOptions)
    $fallback = [regex]::IsMatch($Line,
        '(?:^|\s)' + [regex]::Escape($fallbackText) + '\r?\z', $regexOptions)
    $direction = [regex]::IsMatch($Line,
        '(?:^|\s)LegacyNetA lost connection: Illegal packet received, terminating connection\r?$',
        $regexOptions)
    if ($unknown -or $fallback) {
        if ($Observation.Role -cne 'a' -or -not $Observation.UnknownIdWindow) {
            throw 'Exact unknown-ID diagnostic occurred outside its client-A injection window.'
        }
        # Accept the observed abbreviated console/full-file logger names. The optional
        # bounded timestamp is opaque; thread, severity and logger are mandatory.
        $timestamp = '(?:\[[^\]\r\n]{1,64}\] )?'
        if ($unknown) {
            $match = [regex]::Match($Line,
                '\A' + $timestamp + '\[(?<thread>Netty Client IO #[0-9]+|Render thread)/ERROR\] ' +
                '\[(?:ne\.mi\.ne\.si\.IndexedMessageCodec|net\.minecraftforge\.network\.simple\.IndexedMessageCodec)/SIMPLENET\]: ' +
                [regex]::Escape($unknownText) + '\r?\z', $regexOptions)
            if (-not $match.Success) { throw 'Unknown-ID terminal text lacks the exact native ERROR logger/thread prefix.' }
            if ($match.Groups['thread'].Value -ceq 'Render thread') {
                if ($Observation.NativeUnknownIdCount -ne 1 -or $Observation.NativeUnknownIdNettyCount -ne 1 -or
                    $Observation.NativeUnknownIdRenderCount -ne 0 -or $Observation.NativeUnknownFallbackCount -ne 0) {
                    throw 'Unknown-ID render retry is out of order or repeated.'
                }
                $Observation.NativeUnknownIdRenderCount = 1
                $Observation.NativeUnknownIdCount = 2
            } else {
                if ($Observation.NativeUnknownIdCount -ne 0 -or $Observation.NativeUnknownIdNettyCount -ne 0 -or
                    $Observation.NativeUnknownIdRenderCount -ne 0 -or $Observation.NativeUnknownFallbackCount -ne 0) {
                    throw 'Unknown-ID Netty diagnostic is out of order or repeated.'
                }
                $Observation.NativeUnknownIdNettyCount = 1
                $Observation.NativeUnknownIdCount = 1
            }
        } else {
            $match = [regex]::Match($Line,
                '\A' + $timestamp + '\[Render thread/WARN\] ' +
                '\[(?:minecraft/ClientPacketListener|net\.minecraft\.client\.multiplayer\.ClientPacketListener/)\]: ' +
                [regex]::Escape($fallbackText) + '\r?\z', $regexOptions)
            if (-not $match.Success) { throw 'Unknown-ID fallback lacks the exact native WARN logger/thread prefix.' }
            if ($Observation.NativeUnknownIdCount -ne 2 -or $Observation.NativeUnknownIdNettyCount -ne 1 -or
                $Observation.NativeUnknownIdRenderCount -ne 1 -or $Observation.NativeUnknownFallbackCount -ne 0) {
                throw 'Unknown-ID vanilla fallback is out of order or repeated.'
            }
            $Observation.NativeUnknownFallbackCount = 1
        }
    }
    if ($direction) {
        if ($Observation.Role -cne 'server' -or -not $Observation.WrongDirectionWindow) {
            throw 'Exact direction-disconnect diagnostic occurred outside its server injection window.'
        }
        if ($Observation.NativeWrongDirectionCount -ne 0) { throw 'Duplicate exact direction-disconnect diagnostic.' }
        $Observation.NativeWrongDirectionCount = 1
    }
}

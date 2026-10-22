# Explicit JDK-only helper, not a third Minecraft actor or installed mod.
# Invoked only by an explicit trace mode. The game runs suspend=n if attach fails.
$traceContext=$null
function Start-NetworkReadTrace([string]$ClientDirectory,[int]$ExpectedPid,[bool]$ClearOnly=$false){
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while($true){
        Pump-Logs
        $listeners=@(Get-NetTCPConnection -LocalPort 25592 -State Listen -ErrorAction SilentlyContinue)
        if($listeners.Count){
            if($listeners.Count -ne 1 -or $listeners[0].LocalAddress -cne '127.0.0.1' -or $listeners[0].OwningProcess -ne $ExpectedPid){throw 'Debugger endpoint is not the exact owned client.'}
            break
        }
        if($watch.Elapsed.TotalSeconds -gt 10 -or $actors['a'].Process.HasExited){throw 'Owned client debugger endpoint absent.'}
        Start-Sleep -Milliseconds 50
    }
    $traceJar=Join-Path $project 'fixtures/network-handshake-probe-1.20.1/build/libs/legacybridge-network-read-trace-1.0.0.jar'
    $gson=Join-Path $ClientDirectory 'libraries/com/google/code/gson/gson/2.10/gson-2.10.jar'
    foreach($path in @($traceJar,$gson)){Assert-Plain $path}
    $output=Join-Path $control $(if($ClearOnly){'a/read-clear-trace.json'}else{'a/read-interest-trace.json'})
    $stdout=Join-Path $control 'read-trace-stdout.log';$stderr=Join-Path $control 'read-trace-stderr.log'
    foreach($path in @($output,$stdout,$stderr)){if(Test-Path -LiteralPath $path){throw 'Existing diagnostic evidence preserved.'}}
    $arguments=@('-Xms16m','-Xmx128m','--add-modules=jdk.jdi','-cp',($traceJar+';'+$gson),'dev.legacybridge.validation.trace.HandshakeReadTrace',$output)
    if($ClearOnly){$arguments+=@('clear-only')}
    $argumentFile=Safe-ArgumentFile $control $arguments
    $helper=[Diagnostics.Process]::new()
    $helper.StartInfo.FileName=$java;$helper.StartInfo.Arguments='@"'+$argumentFile+'"'
    $helper.StartInfo.WorkingDirectory=$control;$helper.StartInfo.UseShellExecute=$false;$helper.StartInfo.CreateNoWindow=$true
    $helper.StartInfo.RedirectStandardOutput=$true;$helper.StartInfo.RedirectStandardError=$true
    foreach($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')){$helper.StartInfo.EnvironmentVariables.Remove($name)}
    $context=[pscustomobject]@{Process=$helper;Output=$output;Jar=$traceJar;JarHash=(Hash $traceJar);Gson=$gson;GsonHash=(Hash $gson);Stdout=$stdout;Stderr=$stderr;ClearOnly=$ClearOnly
        Role='trace';Characters=0;Failure=$false;ServerSaved=$false;OutWriter=(New-LogWriter $stdout);ErrWriter=(New-LogWriter $stderr);OutTask=$null;ErrTask=$null}
    if(-not $helper.Start()){$context.OutWriter.Dispose();$context.ErrWriter.Dispose();throw 'Diagnostic helper could not start'}
    $script:traceContext=$context
    $context.OutTask=$helper.StandardOutput.ReadLineAsync();$context.ErrTask=$helper.StandardError.ReadLineAsync()
    Write-Output "NETWORK_READ_TRACE_STARTED pid=$($helper.Id) clientPid=$ExpectedPid loopbackOnly=true"
}
function Stop-NetworkReadTrace {
    if($null -eq $traceContext){return}
    if(-not $traceContext.Process.HasExited){
        $stop=Join-Path $control 'a/trace-stop.request'
        if(-not(Test-Path -LiteralPath $stop)){Write-New $stop '1'}
    }
    $watch=[Diagnostics.Stopwatch]::StartNew()
    while((-not $traceContext.Process.HasExited -or $null -ne $traceContext.OutTask -or $null -ne $traceContext.ErrTask) -and $watch.Elapsed.TotalSeconds -lt 10){Pump-Logs;Start-Sleep -Milliseconds 20}
    if(-not $traceContext.Process.HasExited){throw 'Read-only debugger did not detach; retain owned PID and do not claim cleanup.'}
    $traceContext.OutWriter.Dispose();$traceContext.ErrWriter.Dispose()
}
function Complete-NetworkReadTrace {
    Stop-NetworkReadTrace
    if($traceContext.Process.ExitCode -ne 0){throw 'Read-interest diagnostic failed; inspect its bounded report.'}
    foreach($path in @($traceContext.Output,$traceContext.Stdout,$traceContext.Stderr)){
        Assert-Plain $path
        if((Get-Item -LiteralPath $path).Length -gt 65536){throw 'Trace output exceeds bound.'}
    }
    if((Hash $traceContext.Jar) -cne $traceContext.JarHash -or (Hash $traceContext.Gson) -cne $traceContext.GsonHash){throw 'Diagnostic helper inputs changed.'}
    $data=Get-Content -LiteralPath $traceContext.Output -Raw -Encoding UTF8 | ConvertFrom-Json
    if($traceContext.ClearOnly){
        Assert-NetworkHandshakeClearTrace $data
        return [ordered]@{pid=$traceContext.Process.Id;exitCode=$traceContext.Process.ExitCode;jarSha256=$traceContext.JarHash;gsonSha256=$traceContext.GsonHash
            reportSha256=(Hash $traceContext.Output);reportType=$data.reportType;schemaVersion=$data.schemaVersion;status=$data.status;normalModePassClaimed=$false}
    }
    if($data.schemaVersion -ne 2 -or $data.reportType -cne 'LEGACYBRIDGE_READ_INTEREST_TRACE' -or $data.status -cnotin @('OBSERVED_NO_WITNESS','DIAGNOSTIC_WITNESS') -or
        $data.normalModePassClaimed -cne $false -or $data.executionSuspended -cne $true -or $data.gameDataMutated -cne $false -or
        $data.targetMethodsInvoked -cne $false -or $data.payloadOrCredentialsRead -cne $false -or @($data.records).Count -gt 64 -or @($data.records).Count -lt 1){throw 'Invalid diagnostic report.'}
    $sendSamples=Assert-NetworkHandshakeSendProtocols $data
    return [ordered]@{pid=$traceContext.Process.Id;exitCode=$traceContext.Process.ExitCode;jarSha256=$traceContext.JarHash;gsonSha256=$traceContext.GsonHash
        reportSha256=(Hash $traceContext.Output);status=$data.status;schemaVersion=$data.schemaVersion;sendProtocolSamples=$sendSamples;normalModePassClaimed=$false}
}

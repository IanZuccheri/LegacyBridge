# Developer-only static regression check. Run after the root's serialized build/installDist.
# Executes six CLI conversions serially; never starts Forge, loads an original mod or downloads inputs.
# A failed/partial run is preserved. Choose a new RunLabel instead of overwriting its evidence.
[CmdletBinding()]
param(
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,31}$')]
    [string]$RunLabel = 'comparison1'
)

$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$profileRoot = Join-Path $project 'build/network-snapshot-v1'
$regressions = Join-Path $profileRoot 'regressions'
$run = Join-Path $regressions $RunLabel
$java = 'C:/Program Files/Java/jdk-17/bin/java.exe'
$cliLib = Join-Path $project 'legacy-converter/build/install/legacy-converter/lib'
$logicProfile = Join-Path $project 'build/logic-preserving-v1/mapping-profile.properties'
$lifecycleProfile = Join-Path $project 'build/lifecycle-preserving-v1/mapping-profile.properties'
$results = [Collections.Generic.List[object]]::new()
$fingerprints = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::OrdinalIgnoreCase)
$activeProcess = $null
$activeCase = $null
$activeStarted = $null
$runCreated = $false
$passed = $false
$watch = [Diagnostics.Stopwatch]::StartNew()

$cases = @(
    @{id='simple';profile='simple-content-v1';mapping=$logicProfile;modId='legacybridge_example';input='fixtures/simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar';expected='541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb'},
    @{id='logic';profile='logic-preserving-v1';mapping=$logicProfile;modId='legacybridge_logic';input='fixtures/logic-preservation-1.16.5/build/libs/legacybridge-logic-1.16.5-1.0.0.jar';expected='78de0e2fd62ed83315518a1980ae0738b354531736a5eea73195d332aecc186d'},
    @{id='lifecycle';profile='lifecycle-preserving-v1';mapping=$lifecycleProfile;modId='legacybridge_lifecycle';input='fixtures/lifecycle-content-1.16.5/build/libs/legacybridge-lifecycle-1.16.5-1.0.0.jar';expected='68e9550129d4a2cb88fa184323ae7d4badef8ffb4f31f328e10dfa0eccdba58c'},
    @{id='lifecycle-inferred';profile='lifecycle-preserving-v1';mapping=$lifecycleProfile;modId='legacybridge_lifecycle';input='fixtures/lifecycle-inferred-1.16.5/build/libs/legacybridge-lifecycle-inferred-1.16.5-1.0.0.jar';expected='b3aad65a5871fd5deb1884b332ca3019c9dfbfb4631f9928331db1f29610dd68'},
    @{id='food';profile='food-item-v1';mapping=$logicProfile;modId='legacybridge_food';input='fixtures/food-item-1.16.5/build/libs/legacybridge-food-1.16.5-1.0.0.jar';expected='ecf854aa1826641f6a2ef305b188e685d40178ff9740dc4b5dc1c4f7bff9234e'},
    @{id='datapack';profile='datapack-recipes-v1';mapping=$logicProfile;modId='legacybridge_datapack';input='fixtures/datapack-recipes-1.16.5/build/libs/legacybridge-datapack-recipes-1.16.5-1.0.0.jar';expected='97d4f699c3d31d05ac42288a3946a6d451429d1ecbf86957c3fb14883e2d8979'}
)

function Assert-Plain([string]$Path) {
    $entry = Get-Item -LiteralPath $Path -Force
    while ($null -ne $entry) {
        if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'Regression inputs and outputs must not contain aliases, symlinks or junctions.'
        }
        $entry = if ($entry -is [IO.DirectoryInfo]) { $entry.Parent } else { $entry.Directory }
    }
}
function Hash([string]$Path) {
    Assert-Plain $Path
    if ((Get-Item -LiteralPath $Path).PSIsContainer) { throw 'Expected a regular file.' }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Track([string]$Path) {
    $absolute = [IO.Path]::GetFullPath($Path)
    if (-not $fingerprints.ContainsKey($absolute)) {
        $fingerprints.Add($absolute, [ordered]@{
            path=$absolute;sha256=(Hash $absolute);size=(Get-Item -LiteralPath $absolute).Length
        })
    }
    return $fingerprints[$absolute]
}
function Assert-Unchanged {
    foreach ($entry in @($fingerprints.Values)) {
        if ((Hash $entry.path) -ne $entry.sha256 -or (Get-Item -LiteralPath $entry.path).Length -ne $entry.size) {
            throw ('Tracked regression input changed: ' + [IO.Path]::GetFileName($entry.path))
        }
    }
}
function Write-New([string]$Path, [string]$Text) {
    if (-not [IO.Path]::GetFullPath($Path).StartsWith($run + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Evidence output escaped the fresh regression run.'
    }
    Assert-Plain ([IO.Path]::GetDirectoryName($Path))
    $stream = [IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes($Text)
        $stream.Write($bytes,0,$bytes.Length)
        $stream.Flush($true)
    } finally { $stream.Dispose() }
}
function New-LogWriter([string]$Path) {
    Assert-Plain ([IO.Path]::GetDirectoryName($Path))
    return [IO.StreamWriter]::new([IO.FileStream]::new($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read),[Text.UTF8Encoding]::new($false))
}
function Read-BoundedUtf8([string]$Path, [int]$Limit) {
    Assert-Plain $Path
    $stream = [IO.FileStream]::new($Path,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
    try {
        if ($stream.Length -gt $Limit) { throw 'Structured regression input exceeds its size bound.' }
        $reader = [IO.StreamReader]::new($stream,[Text.UTF8Encoding]::new($false,$true),$false,4096,$true)
        try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $stream.Dispose() }
}
function Track-ExplicitProfile([string]$Path) {
    $null = Track $Path
    $text = Read-BoundedUtf8 $Path 65536
    $keys = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in ($text -split '\r?\n')) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
        $parsed = [regex]::Match($line,'^([^=\s]+)=(.+)$')
        if (-not $parsed.Success) { throw 'Regression profile must use explicit unescaped key=value lines.' }
        $key = $parsed.Groups[1].Value
        $value = $parsed.Groups[2].Value
        if (-not $keys.Add($key)) { throw 'Duplicate explicit mapping-profile key.' }
        if ($key -match 'Mappings$|\.srg$|\.apiJars$') {
            $paths = $value.Split(';')
            if ($paths.Count -gt 16) { throw 'Mapping profile exceeds the per-side API input limit.' }
            foreach ($inputPath in $paths) {
                if (-not [IO.Path]::IsPathRooted($inputPath) -or $inputPath.Contains('\')) {
                    throw 'Audited profiles require explicit absolute forward-slash paths; no discovery or property escapes.'
                }
                $null = Track $inputPath
            }
        }
    }
    foreach ($required in @('legacy.minecraft','legacy.forge','target.minecraft','target.forge','legacy.clientMappings','legacy.serverMappings','legacy.srg','target.clientMappings','target.serverMappings','target.srg','legacy.apiJars','target.apiJars')) {
        if (-not $keys.Contains($required)) { throw ('Missing explicit mapping-profile key: ' + $required) }
    }
}

# All six originals, both explicit data profiles and the installed CLI are checked before output creation.
foreach ($path in @($project,$profileRoot,$cliLib,$java)) { Assert-Plain $path }
if (Test-Path -LiteralPath $run) { throw 'Existing or partial regression output is preserved; choose a fresh RunLabel.' }
$javaRelease = Join-Path ([IO.Path]::GetDirectoryName([IO.Path]::GetDirectoryName($java))) 'release'
if ((Read-BoundedUtf8 $javaRelease 65536) -notmatch '(?m)^JAVA_VERSION="17\.') { throw 'The fixed regression executable must be Java 17.' }
$null = Track $java
$null = Track $javaRelease
$null = Track $PSCommandPath
$cliFiles = @(Get-ChildItem -LiteralPath $cliLib -File -Force | Sort-Object Name)
if ($cliFiles.Count -lt 8 -or $cliFiles.Count -gt 32 -or @($cliFiles | Where-Object { $_.Extension -ne '.jar' }).Count -ne 0) {
    throw 'Unexpected installed CLI library inventory; rerun/review installDist separately.'
}
if (@($cliFiles | Where-Object { $_.Name -like 'legacy-converter-*.jar' }).Count -ne 1) { throw 'Exactly one installed converter JAR is required.' }
foreach ($file in $cliFiles) { $null = Track $file.FullName }
Track-ExplicitProfile $logicProfile
Track-ExplicitProfile $lifecycleProfile
foreach ($case in $cases) {
    $case.inputPath = Join-Path $project $case.input
    $case.original = Track $case.inputPath
}
foreach ($process in @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'")) {
    if ($process.CommandLine -and $process.CommandLine.IndexOf($project,[StringComparison]::OrdinalIgnoreCase) -ge 0) {
        throw 'Finish owned Java/build/game operations before this strictly serial regression run.'
    }
}
if (Test-Path -LiteralPath $regressions) { Assert-Plain $regressions }
else { $null = New-Item -ItemType Directory -Path $regressions }
Assert-Plain $regressions
$null = New-Item -ItemType Directory -Path $run
$runCreated = $true

try {
    foreach ($case in $cases) {
        $activeCase = $case.id
        Assert-Unchanged
        $directory = Join-Path $run $case.id
        $null = New-Item -ItemType Directory -Path $directory
        foreach ($folder in @('empty-user-home','empty-gradle-home','tmp')) {
            $null = New-Item -ItemType Directory -Path (Join-Path $directory $folder)
        }
        $output = Join-Path $directory 'converted.jar'
        $reportPath = Join-Path $directory 'conversion.json'
        $arguments = @('-Xms64m','-Xmx512m','-Dfile.encoding=UTF-8',
            ('-Duser.home=' + (Join-Path $directory 'empty-user-home')),
            ('-Djava.io.tmpdir=' + (Join-Path $directory 'tmp')),
            '-cp',(Join-Path $cliLib '*'),'dev.legacybridge.converter.cli.LegacyBridgeCli',
            'convert',$case.inputPath,'--output',$output,'--conversion-profile',$case.profile,
            '--mapping-profile',$case.mapping,'--report',$reportPath)
        $argumentFile = Join-Path $directory 'cli-arguments.txt'
        $lines = @($arguments | ForEach-Object {
            if ($_ -match '[\r\n\x00]') { throw 'Invalid argument-file control character.' }
            '"' + $_.Replace('\','\\').Replace('"','\"') + '"'
        })
        Write-New $argumentFile (($lines -join [Environment]::NewLine) + [Environment]::NewLine)
        $stdoutPath = Join-Path $directory 'stdout.log'
        $stderrPath = Join-Path $directory 'stderr.log'
        $outWriter = New-LogWriter $stdoutPath
        $errWriter = $null
        $activeProcess = [Diagnostics.Process]::new()
        try {
            $errWriter = New-LogWriter $stderrPath
            $activeProcess.StartInfo.FileName = $java
            $activeProcess.StartInfo.Arguments = '@"' + $argumentFile + '"'
            $activeProcess.StartInfo.WorkingDirectory = $directory
            $activeProcess.StartInfo.UseShellExecute = $false
            $activeProcess.StartInfo.CreateNoWindow = $true
            $activeProcess.StartInfo.RedirectStandardOutput = $true
            $activeProcess.StartInfo.RedirectStandardError = $true
            $activeProcess.StartInfo.EnvironmentVariables['GRADLE_USER_HOME'] = Join-Path $directory 'empty-gradle-home'
            foreach ($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','LEGACYBRIDGE_MAPPING_BUNDLE')) {
                $activeProcess.StartInfo.EnvironmentVariables.Remove($name)
            }
            if (-not $activeProcess.Start()) { throw 'Unable to start the isolated CLI process.' }
            $activeStarted = $activeProcess.StartTime.ToUniversalTime().ToString('o')
            Write-Output "NETWORK_REGRESSION_STARTED case=$($case.id) pid=$($activeProcess.Id)"
            $capture = @(
                [pscustomobject]@{reader=$activeProcess.StandardOutput;writer=$outWriter;task=$activeProcess.StandardOutput.ReadLineAsync()},
                [pscustomobject]@{reader=$activeProcess.StandardError;writer=$errWriter;task=$activeProcess.StandardError.ReadLineAsync()}
            )
            $caseWatch = [Diagnostics.Stopwatch]::StartNew()
            $characters = 0
            while (-not $activeProcess.HasExited -or @($capture | Where-Object { $null -ne $_.task }).Count -gt 0) {
                foreach ($channel in $capture) {
                    for ($batch=0; $batch -lt 64; $batch++) {
                        if ($null -eq $channel.task -or -not $channel.task.IsCompleted) { break }
                        $line = $channel.task.GetAwaiter().GetResult()
                        if ($null -eq $line) { $channel.task = $null; break }
                        $characters += $line.Length
                        if ($line.Length -gt 65536 -or $characters -gt 8388608) { throw 'CLI diagnostic capture exceeds the finite regression bound.' }
                        $channel.writer.WriteLine($line)
                        $channel.writer.Flush()
                        $channel.task = $channel.reader.ReadLineAsync()
                    }
                }
                if ($caseWatch.Elapsed.TotalSeconds -gt 180) { throw 'CLI regression deadline; do not start another operation while its recorded PID remains alive.' }
                Start-Sleep -Milliseconds 20
            }
            $activeProcess.WaitForExit()
            if ($activeProcess.ExitCode -ne 0) { throw ('CLI conversion failed for ' + $case.id + ' with exit code ' + $activeProcess.ExitCode) }
            $processId = $activeProcess.Id
            $processExit = $activeProcess.ExitCode
        } finally {
            $outWriter.Dispose()
            if ($null -ne $errWriter) { $errWriter.Dispose() }
        }

        Assert-Unchanged
        $report = (Read-BoundedUtf8 $reportPath 4194304) | ConvertFrom-Json
        $outputHash = Hash $output
        if ($report.schemaVersion -ne 2 -or $report.reportType -ne 'LEGACY_CONVERSION' -or
            $report.status -ne 'CONVERTED' -or $report.transformationsCommitted -ne $true -or
            $report.bytecodeValidation.status -ne 'PASSED' -or $report.bytecodeValidation.modClassesLoaded -ne $false -or
            $report.input.sha256 -ne $case.original.sha256 -or $report.input.fileName -ne [IO.Path]::GetFileName($case.inputPath) -or
            $report.output.sha256 -ne $outputHash -or $report.output.fileName -ne 'converted.jar' -or
            $report.modId -ne $case.modId -or $report.modVersion -ne '1.0.0') {
            throw ('Missing/mismatched structured conversion evidence for ' + $case.id)
        }
        if ($outputHash -ne $case.expected) { throw ('NON_IDENTICAL_REGRESSION_OUTPUT: ' + $case.id + '; expected=' + $case.expected + '; actual=' + $outputHash) }
        if ($case.profile -ne 'simple-content-v1' -and $report.supportProfile -ne $case.profile) {
            throw ('The conversion report names a different support profile for ' + $case.id)
        }
        $results.Add([ordered]@{
            id=$case.id;conversionProfile=$case.profile;status='BYTE_IDENTICAL';expectedSha256=$case.expected;outputSha256=$outputHash
            originalSha256=$case.original.sha256;originalSize=$case.original.size;originalIntact=$true
            mappingProfileSha256=$fingerprints[[IO.Path]::GetFullPath($case.mapping)].sha256
            reportPath=($case.id + '/conversion.json');reportSha256=(Hash $reportPath);reportSchemaVersion=2
            bytecodeStatus='PASSED';reportedModClassesLoaded=$false;runtimeValidation='NOT_RUN'
            stdoutSha256=(Hash $stdoutPath);stderrSha256=(Hash $stderrPath);argumentsSha256=(Hash $argumentFile)
            pid=$processId;startedUtc=$activeStarted;exitCode=$processExit;elapsedSeconds=[int]$caseWatch.Elapsed.TotalSeconds
        })
        $activeProcess.Dispose()
        $activeProcess = $null
        $activeStarted = $null
        Write-Output "NETWORK_REGRESSION_BYTE_IDENTICAL case=$($case.id) sha256=$outputHash"
    }
    Assert-Unchanged
    # Detect extra/replaced libraries as well as changes to previously fingerprinted files.
    $finalNames = @((Get-ChildItem -LiteralPath $cliLib -File -Force | Sort-Object Name).Name)
    if (($finalNames -join "`n") -ne (($cliFiles.Name) -join "`n")) { throw 'Installed CLI library set changed during the run.' }
    $receipt = [ordered]@{
        schemaVersion=1;reportType='NETWORK_PROFILE_REGRESSION';status='PASSED';runLabel=$RunLabel
        exactByteIdenticalOutputs=6;expectedCases=6;serial=$true;javaMajor=17;maxHeapMiB=512
        javaSha256=$fingerprints[[IO.Path]::GetFullPath($java)].sha256
        explicitLocalMappingProfiles=$true;gradleCacheFallback=$false;downloadsRequested=$false;runtimeValidation='NOT_RUN'
        originalClassesExecutedByThisScript=$false;userEnvironmentModified=$false
        inputs=@($fingerprints.Values | Sort-Object path);results=@($results.ToArray());elapsedSeconds=[int]$watch.Elapsed.TotalSeconds
    }
    Write-New (Join-Path $run 'regression-receipt.json') (($receipt | ConvertTo-Json -Depth 12) + [Environment]::NewLine)
    $passed = $true
    Write-Output "LEGACYBRIDGE_NETWORK_REGRESSIONS_PASSED runLabel=$RunLabel byteIdentical=6 runtime=NOT_RUN"
} catch {
    $failureRecord = $_
    if ($runCreated) {
        $remaining = $null
        if ($null -ne $activeProcess) {
            try {
                if (-not $activeProcess.HasExited) {
                    $remaining = [ordered]@{pid=$activeProcess.Id;startedUtc=$activeStarted;case=$activeCase}
                    Write-Warning "REGRESSION_CLI_STILL_RUNNING pid=$($activeProcess.Id); inspect this owned process before any further build/game/conversion. No process was killed."
                }
            } catch { Write-Warning 'Unable to inspect CLI process state; do not assume it has exited.' }
        }
        $failure = [ordered]@{
            schemaVersion=1;reportType='NETWORK_PROFILE_REGRESSION';status='FAILED_OR_INCOMPLETE';runLabel=$RunLabel
            activeCase=$activeCase;completedCases=$results.Count;results=@($results.ToArray());remainingOwnedProcess=$remaining
            causeType=$failureRecord.Exception.GetType().Name;cause=$failureRecord.Exception.Message;runtimeValidation='NOT_RUN';passClaimed=$false
            artifactsPreserved=$true;inputs=@($fingerprints.Values | Sort-Object path)
        }
        Write-New (Join-Path $run 'regression-failure.json') (($failure | ConvertTo-Json -Depth 12) + [Environment]::NewLine)
    }
    throw
} finally {
    if ($null -ne $activeProcess) { $activeProcess.Dispose() }
}

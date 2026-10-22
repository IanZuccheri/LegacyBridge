# Local, read-only observation of finalized P6 inputs, products and evidence.
# The sole mutation with -Collect is a fresh evidence JSON (and its owned publication temporary).
# No build, game launch, upload, account file, launch-argument file, or log text is copied.
[CmdletBinding()]
param(
    [switch]$Collect,
    [ValidatePattern('^[a-zA-Z0-9][a-zA-Z0-9._-]{0,100}\.json$')]
    [string]$ManifestName = ('p6-evidence-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [Guid]::NewGuid().ToString('N') + '.json')
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$expectedPublic = '00cc7ebde1279c1ac3cc6ac0a54498e4598ca4f45700a6799f915ce9dddea991'
$expectedOriginal = '00dd689f53f9631ecf4a8e84da142eb21051e2ca33f1c05403e4060e15b10947'
$expectedConverted = '541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb'
$pending = [Collections.Generic.List[string]]::new()
$issues = [Collections.Generic.List[object]]::new()
$maxFileBytes = 268435456L

function Assert-P6EvidencePath([string]$Path, [bool]$Directory) {
    $absolute = [IO.Path]::GetFullPath($Path)
    $entry = Get-Item -LiteralPath $absolute -Force
    if ([bool]$entry.PSIsContainer -ne $Directory) { throw 'Unexpected file/directory kind in P6 evidence.' }
    $current = $entry
    while ($null -ne $current) {
        if (($current.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Reparse points are refused in P6 evidence paths.' }
        $current = if ($current -is [IO.DirectoryInfo]) { $current.Parent } else { $current.Directory }
    }
    return $entry.FullName.TrimEnd('\')
}
$project = Assert-P6EvidencePath (Join-Path $PSScriptRoot '..') $true
$run = Assert-P6EvidencePath (Join-Path $project 'build/p6-public') $true
$evidence = Assert-P6EvidencePath (Join-Path $run 'evidence') $true
$destination = Join-Path $evidence $ManifestName
if (Test-Path -LiteralPath $destination) { throw 'Existing evidence is never overwritten; choose a fresh ManifestName.' }
if (-not $Collect) {
    Write-Output 'P6_EVIDENCE_PREFLIGHT_OK: paths and output name checked; no collection, file publication or game operation performed. Run -Collect only after sessions are finalized.'
    return
}
# Command lines are inspected in memory solely for owned-process refusal and never stored or emitted.
$ownedRunning = @(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'" | Where-Object {
    $_.CommandLine -and $_.CommandLine.IndexOf($run, [StringComparison]::OrdinalIgnoreCase) -ge 0
})
if ($ownedRunning.Count -ne 0) { throw 'An owned P6 Java process is still running; finalize sessions before collecting.' }

function Get-P6Relative([string]$Path) {
    $absolute = [IO.Path]::GetFullPath($Path)
    $prefix = $project + '\'
    if (-not $absolute.StartsWith($prefix, [StringComparison]::OrdinalIgnoreCase)) { throw 'Evidence path escapes the selected project.' }
    return $absolute.Substring($prefix.Length).Replace('\', '/')
}
function Add-P6Issue([string]$Path, [string]$Code) {
    $issues.Add([ordered]@{ path = (Get-P6Relative $Path); code = $Code })
}
function Get-P6Files([string]$Directory, [int]$Depth = 0) {
    if (-not (Test-Path -LiteralPath $Directory)) { $pending.Add('MISSING_DIRECTORY:' + (Get-P6Relative $Directory)); return }
    $null = Assert-P6EvidencePath $Directory $true
    $queue = [Collections.Generic.Queue[object]]::new()
    $queue.Enqueue(@{ path = $Directory; depth = 0 })
    $examined = 0
    while ($queue.Count -ne 0) {
        $next = $queue.Dequeue()
        foreach ($entry in @(Get-ChildItem -LiteralPath $next.path -Force | Sort-Object Name)) {
            if (++$examined -gt 4096) { throw 'P6 evidence enumeration exceeded 4096 entries per selected root.' }
            if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { Add-P6Issue $entry.FullName 'REPARSE_POINT_NOT_READ'; continue }
            if ($entry.PSIsContainer) {
                if ($next.depth -lt $Depth) { $queue.Enqueue(@{ path = $entry.FullName; depth = ($next.depth + 1) }) }
            } else { Write-Output $entry.FullName }
        }
    }
}
function Get-P6Fingerprint([string]$Path) {
    $canonical = Assert-P6EvidencePath $Path $false
    $before = Get-Item -LiteralPath $canonical -Force
    if ($before.Length -gt $maxFileBytes) { throw 'Selected evidence file exceeds 256 MiB collection limit.' }
    $sha = (Get-FileHash -LiteralPath $canonical -Algorithm SHA256).Hash.ToLowerInvariant()
    $after = Get-Item -LiteralPath $canonical -Force
    $null = Assert-P6EvidencePath $canonical $false
    if ($before.Length -ne $after.Length -or $before.LastWriteTimeUtc -ne $after.LastWriteTimeUtc) { throw 'Evidence changed while hashing; retry only after the session stops.' }
    return [ordered]@{ path = (Get-P6Relative $canonical); bytes = $after.Length; sha256 = $sha; lastWriteUtc = $after.LastWriteTimeUtc.ToString('o') }
}
function Get-P6Field($Object, [string]$Name) {
    if ($null -eq $Object) { return $null }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value
}
function Get-P6Token($Value, [string]$Pattern = '^[A-Z][A-Z0-9_]{0,100}$') {
    if ($Value -is [string] -and $Value -cmatch $Pattern) { return $Value }
    return $null
}
function Read-P6Json([string]$Path) {
    $null = Assert-P6EvidencePath $Path $false
    if ((Get-Item -LiteralPath $Path).Length -gt 2097152) { throw 'JSON exceeds the 2 MiB evidence-summary limit.' }
    # Summaries retain selected primitive fields only, never arbitrary report/log text.
    return ([IO.File]::ReadAllText($Path, [Text.UTF8Encoding]::new($false, $true)) | ConvertFrom-Json)
}

$artifactGroups = [ordered]@{}
foreach ($selection in @(
    @{ name = 'clientInput'; relative = 'client/legacybridge/input'; depth = 0 },
    @{ name = 'clientStaging'; relative = 'client/legacybridge/staging'; depth = 2 },
    @{ name = 'clientInstalledMods'; relative = 'client/mods'; depth = 0 },
    @{ name = 'serverInstalledMods'; relative = 'server/mods'; depth = 0 },
    @{ name = 'negativeFixtureOriginals'; relative = 'negative-fixtures'; depth = 0 }
)) {
    $files = @(Get-P6Files (Join-Path $run $selection.relative) $selection.depth | Where-Object { [IO.Path]::GetExtension($_) -ieq '.jar' })
    $records = @($files | ForEach-Object {
        $item = Get-P6Fingerprint $_
        $item['matchesExpectedPublic'] = $item.sha256 -eq $expectedPublic
        $item['matchesExpectedSourceFixture'] = $item.sha256 -eq $expectedOriginal
        $item['matchesExpectedConvertedFixture'] = $item.sha256 -eq $expectedConverted
        $item
    })
    $artifactGroups[$selection.name] = [ordered]@{ count = $records.Count; files = $records }
}
$fixedArtifacts = [Collections.Generic.List[object]]::new()
foreach ($selection in @(
    @{ role = 'finalPublicBuild'; path = 'legacy-product/build/libs/LegacyBridge.jar'; hash = $expectedPublic },
    @{ role = 'genuineSourceFixture'; path = 'fixtures/simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar'; hash = $expectedOriginal },
    @{ role = 'negativeFixtureManifest'; path = 'build/p6-public/negative-fixtures/fixture-manifest.json'; hash = $null }
)) {
    $path = Join-Path $project $selection.path
    if (-not (Test-Path -LiteralPath $path)) { $pending.Add('MISSING_ARTIFACT:' + $selection.path); continue }
    $item = Get-P6Fingerprint $path
    $item['role'] = $selection.role
    if ($null -ne $selection.hash) { $item['expectedSha256'] = $selection.hash; $item['matchesExpected'] = $item.sha256 -eq $selection.hash }
    $fixedArtifacts.Add($item)
}
if (@($artifactGroups.clientInstalledMods.files | Where-Object { $_.matchesExpectedConvertedFixture }).Count -eq 0) { $pending.Add('NO_BASELINE_CONVERTED_JAR_CURRENTLY_IN_CLIENT_MODS') }
if (@($artifactGroups.serverInstalledMods.files | Where-Object { $_.matchesExpectedConvertedFixture }).Count -eq 0) { $pending.Add('NO_BASELINE_CONVERTED_JAR_CURRENTLY_IN_SERVER_MODS') }

$reportSummaries = [Collections.Generic.List[object]]::new()
$schemaCounts = @{}
$errorCounts = @{}
$stateCounts = @{}
foreach ($root in @('client/legacybridge/reports', 'client/legacybridge/transactions')) {
    foreach ($path in @(Get-P6Files (Join-Path $run $root) 2 | Where-Object { [IO.Path]::GetExtension($_) -ieq '.json' })) {
        $item = Get-P6Fingerprint $path
        try {
            $parsed = Read-P6Json $path
            $schema = Get-P6Field $parsed 'schemaVersion'
            $type = Get-P6Token (Get-P6Field $parsed 'reportType')
            if ($null -eq $type) {
                $type = switch ([IO.Path]::GetFileName($path)) { 'analysis.json' { 'COMPATIBILITY_REPORT_FILENAME' }; 'transformation-log.json' { 'TRANSFORMATION_LOG_FILENAME' }; default { 'UNCLASSIFIED_JSON' } }
            }
            $item['reportTypeOrFilenameClassification'] = $type
            $item['schemaVersion'] = if ($schema -is [int] -or $schema -is [long]) { $schema } else { $null }
            $schemaKey = $type + ':' + [string]$item.schemaVersion
            if (-not $schemaCounts.ContainsKey($schemaKey)) { $schemaCounts[$schemaKey] = 0 }; $schemaCounts[$schemaKey]++
            foreach ($field in @('state', 'status', 'compatibilityStatus')) {
                $value = Get-P6Token (Get-P6Field $parsed $field)
                if ($null -ne $value) { $item[$field] = $value }
            }
            foreach ($field in @('bytecodeExecuted', 'bytecodeModified', 'transformationsCommitted')) {
                $value = Get-P6Field $parsed $field
                if ($value -is [bool]) { $item[$field] = $value }
            }
            foreach ($field in @('inputSha256', 'artifactSha256')) {
                $value = Get-P6Token (Get-P6Field $parsed $field) '^[0-9a-f]{64}$'
                if ($null -ne $value) { $item[$field] = $value }
            }
            $code = Get-P6Token (Get-P6Field (Get-P6Field $parsed 'error') 'code')
            if ($null -ne $code) {
                $item['errorCode'] = $code
                if (-not $errorCounts.ContainsKey($code)) { $errorCounts[$code] = 0 }; $errorCounts[$code]++
            }
            $state = Get-P6Token (Get-P6Field $parsed 'state')
            if ($type -eq 'LEGACYBRIDGE_JOB' -and $null -ne $state) {
                if (-not $stateCounts.ContainsKey($state)) { $stateCounts[$state] = 0 }; $stateCounts[$state]++
            }
            $diagnostics = @(Get-P6Field $parsed 'diagnostics')
            $item['knownDiagnosticObservations'] = [ordered]@{
                resourceOutsideSubset = @($diagnostics | Where-Object { $_ -is [string] -and $_.StartsWith('RESOURCE_OUTSIDE_SUBSET:') }).Count
                thirdPartyDependencyManualMigration = @($diagnostics | Where-Object { $_ -is [string] -and $_.StartsWith('Third-party dependency requires manual migration:') }).Count
            }
            $embeddedLog = Get-P6Field $parsed 'transformationLog'
            if ($null -ne $embeddedLog) {
                $embeddedSchema = Get-P6Field $embeddedLog 'schemaVersion'
                if ($embeddedSchema -is [int] -or $embeddedSchema -is [long]) { $item['embeddedTransformationSchemaVersion'] = $embeddedSchema }
            }
            $item['summaryStatus'] = 'PARSED_SELECTED_FIELDS_NOT_SCHEMA_VALIDATED'
        } catch {
            $item['summaryStatus'] = 'UNREADABLE_OR_OVER_BUDGET'
            Add-P6Issue $path 'REPORT_SUMMARY_UNAVAILABLE'
        }
        $reportSummaries.Add($item)
    }
}
foreach ($state in @('CONVERTED', 'BLOCKED', 'CANCELLED', 'FAILED', 'PENDING_RESTART')) {
    if (-not $stateCounts.ContainsKey($state)) { $pending.Add('NO_PERSISTED_JOB_RECEIPT_STATE:' + $state) }
}

function Measure-P6Trace([string]$Path) {
    $counts = [ordered]@{ classLoadLines = 0; unrecognizedNonemptyLines = 0; developmentCacheSources = 0; qaHarnessClasses = 0; originalFixtureClasses = 0; originalFixtureJarSources = 0; generatedConvertedClasses = 0; runtimeClasses = 0; productClientClasses = 0; minecraftClientClasses = 0 }
    $stream = [IO.File]::OpenRead($Path)
    $reader = $null
    try {
        if ($Path.EndsWith('.gz', [StringComparison]::OrdinalIgnoreCase)) { $stream = [IO.Compression.GZipStream]::new($stream, [IO.Compression.CompressionMode]::Decompress) }
        $reader = [IO.StreamReader]::new($stream, [Text.UTF8Encoding]::new($false, $true), $true)
        $characters = 0L
        while ($null -ne ($line = $reader.ReadLine())) {
            $characters += $line.Length
            if ($line.Length -gt 65536 -or $characters -gt $maxFileBytes) { throw 'Trace exceeds bounded line/character capture limit.' }
            if ($line -match '^\[[^\r\n]*\]\[class,load\]\s+([A-Za-z0-9_.$/]+)\s+source:\s+(.+)$') {
                $className = $Matches[1]; $source = $Matches[2]; $counts.classLoadLines++
                if ($source -match '(?i)[/\\]\.gradle[/\\]') { $counts.developmentCacheSources++ }
                if ($className -match '^dev\.legacybridge\.(product\.validation|validation)\.') { $counts.qaHarnessClasses++ }
                if ($className -match '^dev\.legacybridge\.fixture\.') { $counts.originalFixtureClasses++ }
                if ($source -match '(?i)legacybridge-example-1\.16\.5[^/\\]*\.jar') { $counts.originalFixtureJarSources++ }
                if ($className -match '^dev\.legacybridge\.generated\.') { $counts.generatedConvertedClasses++ }
                if ($className -match '^dev\.legacybridge\.runtime\.') { $counts.runtimeClasses++ }
                if ($className -match '^dev\.legacybridge\.product\.client\.') { $counts.productClientClasses++ }
                if ($className -match '^net\.minecraft\.client\.') { $counts.minecraftClientClasses++ }
            } elseif (-not [string]::IsNullOrWhiteSpace($line)) { $counts.unrecognizedNonemptyLines++ }
        }
    } finally { if ($null -ne $reader) { $reader.Dispose() } else { $stream.Dispose() } }
    return [ordered]@{ observation = $(if ($counts.classLoadLines -gt 0) { 'PARSED_CLASS_LOADING_ONLY' } else { 'NO_RECOGNIZED_CLASS_LOADING_DATA' }); counts = $counts;
        absenceScope = 'Zero matches describe only recognized records in this trace; not filesystem access denial, no-execution certification, networking isolation, or complete startup proof.' }
}
$logPaths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$screenshots = [Collections.Generic.List[object]]::new()
foreach ($path in @(Get-P6Files $evidence 4)) {
    if ($path -match '(?i)\.png$') { $screenshots.Add((Get-P6Fingerprint $path)) }
    if ($path -match '(?i)\.log(\.gz)?$') { $null = $logPaths.Add($path) }
}
foreach ($relative in @('client', 'client/logs', 'server', 'server/logs')) {
    foreach ($path in @(Get-P6Files (Join-Path $run $relative) 0 | Where-Object { $_ -match '(?i)\.log(\.gz)?$' })) { $null = $logPaths.Add($path) }
}
if (Test-Path -LiteralPath (Join-Path $run 'build-baseline.log')) { $null = $logPaths.Add((Join-Path $run 'build-baseline.log')) }
$logs = [Collections.Generic.List[object]]::new()
$traceCount = 0
foreach ($path in @($logPaths | Sort-Object)) {
    $item = Get-P6Fingerprint $path
    $item['rawContentCopied'] = $false
    if ([IO.Path]::GetFileName($path) -match '(?i)classload') {
        $traceCount++
        try { $item['classLoadingObservation'] = Measure-P6Trace $path }
        catch { $item['classLoadingObservation'] = @{ observation = 'UNREADABLE_OR_OVER_BUDGET' }; Add-P6Issue $path 'CLASS_TRACE_SUMMARY_UNAVAILABLE' }
    }
    $logs.Add($item)
}
if ($traceCount -eq 0) { $pending.Add('NO_CLASS_LOADING_TRACES') }
if ($screenshots.Count -eq 0) { $pending.Add('NO_SCREENSHOTS_FOR_INDEPENDENT_REVIEW') }
foreach ($launch in @('launch-1', 'launch-2')) {
    if (-not (Test-Path -LiteralPath (Join-Path $evidence $launch))) { $pending.Add('MISSING_LAUNCH_ARCHIVE:' + $launch) }
}

$serverReceipt = [ordered]@{ observation = 'PENDING'; statusClaim = $null }
$receiptPath = Join-Path $run 'server/p6-public-server-receipt.json'
if (Test-Path -LiteralPath $receiptPath) {
    $serverReceipt['file'] = Get-P6Fingerprint $receiptPath
    try {
        $parsed = Read-P6Json $receiptPath
        $schema = Get-P6Field $parsed 'schemaVersion'
        if ($schema -is [int] -or $schema -is [long]) { $serverReceipt['schemaVersion'] = $schema }
        $serverReceipt['statusClaim'] = Get-P6Token (Get-P6Field $parsed 'status')
        foreach ($field in @('exitCode', 'forcedTermination', 'externalStdinOnly', 'qaMod', 'mappingBundle', 'initialUserHomeEmpty', 'offlineClaim')) {
            $value = Get-P6Field $parsed $field
            if ($value -is [bool] -or $value -is [int] -or $value -is [long]) { $serverReceipt[$field] = $value }
        }
        $serverReceipt['checkClaims'] = @(@(Get-P6Field $parsed 'checks') | ForEach-Object { Get-P6Token $_ '^[a-z][a-z0-9_]{0,100}$' } | Where-Object { $null -ne $_ })
        $serverReceipt['observation'] = 'SELECTED_RUNNER_RECEIPT_FIELDS_NOT_INDEPENDENT_REEXECUTION'
    } catch { $serverReceipt['observation'] = 'UNREADABLE_OR_OVER_BUDGET'; Add-P6Issue $receiptPath 'SERVER_RECEIPT_SUMMARY_UNAVAILABLE' }
} else { $pending.Add('DEDICATED_SERVER_RECEIPT_NOT_AVAILABLE') }
if ($serverReceipt.statusClaim -ne 'PASSED') { $pending.Add('DEDICATED_SERVER_RUNNER_HAS_NOT_REPORTED_PASSED') }

$manifest = [ordered]@{
    schemaVersion = 1; reportType = 'LEGACYBRIDGE_P6_EVIDENCE_INDEX'; generatedUtc = [DateTime]::UtcNow.ToString('o');
    status = 'OBSERVATIONS_COLLECTED_NOT_RELEASE_CERTIFICATION'; minecraft = '1.20.1'; forge = '47.4.23'; javaMajor = 17;
    expectedPublicSha256 = $expectedPublic; expectedSourceSha256 = $expectedOriginal; expectedConvertedSha256 = $expectedConverted;
    collector = (Get-P6Fingerprint $PSCommandPath); fixedArtifacts = $fixedArtifacts.ToArray(); artifactGroups = $artifactGroups;
    reports = [ordered]@{ count = $reportSummaries.Count; schemasObserved = $schemaCounts; jobStatesObserved = $stateCounts; errorCodesObserved = $errorCounts; files = $reportSummaries.ToArray() };
    screenshots = [ordered]@{ count = $screenshots.Count; interpretation = 'NOT_PERFORMED_BY_COLLECTOR'; files = $screenshots.ToArray() };
    logs = [ordered]@{ count = $logs.Count; classLoadingTraceCount = $traceCount; files = $logs.ToArray() };
    dedicatedServer = $serverReceipt; pending = @($pending | Sort-Object -Unique); issues = $issues.ToArray();
    privacy = [ordered]@{ accountFilesRead = $false; launchArgumentFilesRead = $false; rawLogLinesCopied = $false; rawErrorMessagesCopied = $false; uploads = $false };
    evidenceBoundaries = [ordered]@{
        static = 'Hashes and selected report fields are observations, not new bytecode/reference validation or inferred compatibility.';
        ui = 'Screenshots require separate human review. Persisted job states do not alone prove native clicks, cancellation timing, asset rendering or recipe assembly.';
        installation = 'Files currently in mods are not proof they were loaded. Compare session-specific class traces and native behavior evidence separately.';
        classLoading = 'Each archived/current trace is reported independently; absence of .gradle in sources is not a filesystem sandbox or proof of no cache file access.';
        network = 'NOT_ISOLATED: no network isolation was established by this collector or ordinary test launchers; no offline certification is claimed.';
        historical = 'Missing prior logs, state receipts and screenshots remain pending. A launch archive is not reconstructed from later files.';
        release = 'No compatibility-universal, launcher-restart, multiplayer or redistribution-rights approval is asserted.'
    }
}
$json = $manifest | ConvertTo-Json -Depth 16
$temporary = Join-Path $evidence ('.p6-evidence-' + [Guid]::NewGuid().ToString('N') + '.tmp')
$stream = [IO.FileStream]::new($temporary, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
try {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($json)
    $stream.Write($bytes, 0, $bytes.Length); $stream.Flush($true)
} finally { $stream.Dispose() }
# The two-argument .NET File.Move refuses an existing final destination; no replacement overload is used.
# This is only evidence publication; it does not claim all-file/power-loss transactional guarantees.
$null = Assert-P6EvidencePath $evidence $true
[IO.File]::Move($temporary, $destination)
Write-Output ('P6_EVIDENCE_COLLECTED ' + (Get-P6Relative $destination))
Write-Output ('No release certification: pending=' + $pending.Count + ', issues=' + $issues.Count + '. Inspect the indexed evidence and independent validation ledger.')

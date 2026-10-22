# Headless, synthetic source-export tests. No Gradle, original mods, games or network.
[CmdletBinding()]
param([string]$WorkingDirectory = '')
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$project = Split-Path -Parent $PSScriptRoot
$export = Join-Path $PSScriptRoot 'Export-PublicSource.ps1'
$wrapper = Join-Path $project 'gradle/wrapper/gradle-wrapper.jar'
$utf8 = [Text.UTF8Encoding]::new($false)
if (-not $WorkingDirectory) {
    $WorkingDirectory = Join-Path $project ('build/public-source-export-tests-' + [Guid]::NewGuid().ToString('N'))
}
$testRoot = [IO.Path]::GetFullPath($WorkingDirectory)
if (Test-Path -LiteralPath $testRoot) { throw 'Use a new test directory; tests never overwrite or delete prior evidence.' }
[void][IO.Directory]::CreateDirectory($testRoot)
$passed = 0
$skipped = 0

function Write-TestText([string]$Root, [string]$Relative, [string]$Text) {
    $file = Join-Path $Root $Relative
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $file))
    [IO.File]::WriteAllText($file, $Text, $utf8)
}
function Assert-Export([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Export test failed: $Message" }
}
function New-TestSource([string]$Name) {
    $root = Join-Path $testRoot ($Name + '/input')
    foreach ($relative in @('LICENSE', 'README.md', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat',
            'gradle/wrapper/gradle-wrapper.properties', 'legacy-compat-api/build.gradle', 'legacy-converter/build.gradle',
            'legacy-corpus/build.gradle', 'legacy-product/build.gradle', 'legacy-runtime/build.gradle', 'legacy-workflow/build.gradle')) {
        Write-TestText $root $relative "original synthetic source`n"
    }
    Copy-Item -LiteralPath $wrapper -Destination (Join-Path $root 'gradle/wrapper/gradle-wrapper.jar')
    Write-TestText $root 'legacy-converter/src/main/java/test/Example.java' 'class Example { String p = "C:/test/../other"; }'
    Write-TestText $root 'fixtures/original-1.16.5/src/main/resources/META-INF/mods.toml' 'license="All Rights Reserved" # historical test input'
    Write-TestText $root 'fixtures/original-1.16.5/build.gradle' '// authored original fixture'
    return $root
}
function Expect-ExportFailure([string]$InputRoot, [string]$Destination, [string]$ExpectedMessage) {
    $failure = $null
    try { & $export -SourceRoot $InputRoot -Destination $Destination | Out-Null }
    catch { $failure = $_.Exception.Message }
    Assert-Export ($null -ne $failure) 'expected export rejection'
    Assert-Export ($failure -like ('*' + $ExpectedMessage + '*')) ('expected rejection category: ' + $ExpectedMessage)
    return $failure
}
function Pass([string]$Name) { $script:passed++; Write-Output ('PASS ' + $Name) }

$inputRoot = New-TestSource 'positive'
$target = Join-Path $testRoot 'positive/output'
$original = Join-Path $inputRoot 'legacy-converter/src/main/java/test/Example.java'
$originalHash = (Get-FileHash -LiteralPath $original -Algorithm SHA256).Hash
$slash = $inputRoot.Replace('\', '/')
Write-TestText $inputRoot 'docs/contract/status.json' ('{"source":"' + $slash + '/build/private.jar","status":"NOT_RUN"}')
Write-TestText $inputRoot 'docs/EXAMPLE.md' ($inputRoot + "`n" + $inputRoot.Replace('\', '\\') + "`n" + $slash.Replace(' ', '%20'))
Write-TestText $inputRoot 'corpus/licenses/upstream/LICENSE' 'Original upstream license, not relicensed.'
Write-TestText $inputRoot 'corpus/records/example.json' '{"output":{"status":"BLOCKED"}}'
foreach ($folder in @('build', '.gradle', 'run', 'runs', 'logs', 'bin', 'local-private', '.git')) {
    Write-TestText $inputRoot ($folder + '/private.txt') 'must never enter snapshot'
    Write-TestText $inputRoot ('fixtures/original-1.16.5/' + $folder + '/private.txt') 'must never enter snapshot'
}
Write-TestText $inputRoot 'installer.log' 'not public'
Write-TestText $inputRoot 'legacy-bridge/legacy-compat-api/src/duplicate.java' 'nested accidental project must not enter snapshot'
Write-TestText $inputRoot 'unlisted-private-folder/private.txt' 'not public'
& $export -SourceRoot $inputRoot -Destination $target | Out-Null
$receiptPath = Join-Path (Split-Path -Parent $target) 'output-export-manifest.json'
$receipt = Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
Assert-Export ($receipt.status -eq 'SOURCE_EXPORT_COMPLETE_NOT_PUBLISHED') 'completed receipt'
Assert-Export (-not (Test-Path -LiteralPath (Join-Path $target 'output-export-manifest.json'))) 'receipt stays outside public source'
Assert-Export ((Get-FileHash -LiteralPath $original -Algorithm SHA256).Hash -eq $originalHash) 'original Java file unchanged'
Assert-Export ((Get-FileHash -LiteralPath (Join-Path $target 'legacy-converter/src/main/java/test/Example.java') -Algorithm SHA256).Hash -eq $originalHash) 'synthetic absolute traversal path preserved'
Pass 'source preservation and external receipt'

foreach ($entry in $receipt.files) {
    Assert-Export ($entry.path -notmatch '(^|/)(build|\.gradle|run|runs|logs|bin|local-private|\.git)/') 'excluded tree absent'
    Assert-Export ($entry.path -notmatch '^legacy-bridge/|installer\.log') 'excluded accidental tree/log absent'
    Assert-Export ((Get-FileHash -LiteralPath (Join-Path $target $entry.path) -Algorithm SHA256).Hash.ToLowerInvariant() -eq $entry.sha256) 'every output hash verified'
}
$binary = @($receipt.files | Where-Object { $_.path -match '\.(jar|zip|class|dll|exe|db|sqlite|png|ogg)$' })
Assert-Export ($binary.Count -eq 1 -and $binary[0].path -eq 'gradle/wrapper/gradle-wrapper.jar') 'only exact wrapper binary'
Pass 'recursive exclusions and exact wrapper-only binary policy'

$redacted = Get-Content -LiteralPath (Join-Path $target 'docs/contract/status.json') -Raw | ConvertFrom-Json
Assert-Export ($redacted.source -eq '${LEGACYBRIDGE_SOURCE_ROOT}/build/private.jar') 'JSON remains valid with private root placeholder'
Assert-Export ((Get-Content -LiteralPath (Join-Path $target 'docs/EXAMPLE.md') -Raw) -notlike ('*' + $inputRoot + '*')) 'raw path is redacted'
Assert-Export ((Get-Content -LiteralPath (Join-Path $inputRoot 'docs/contract/status.json') -Raw).Contains($slash)) 'original historical JSON untouched'
Assert-Export ((Get-Content -LiteralPath (Join-Path $target 'fixtures/original-1.16.5/src/main/resources/META-INF/mods.toml') -Raw).Contains('All Rights Reserved')) 'historic fixture metadata preserved'
Pass 'raw, escaped, URL-root redaction without changing historical originals'

$marker = Join-Path $target 'owned-marker.txt'
[IO.File]::WriteAllText($marker, 'preserve', $utf8)
[void](Expect-ExportFailure $inputRoot $target 'Destination already exists')
Assert-Export ((Get-Content -LiteralPath $marker -Raw) -eq 'preserve') 'existing output not touched'
Pass 'existing destination never overwritten'

$inputRoot = New-TestSource 'credential'
$secret = 'ghp_' + ('a' * 30)
Write-TestText $inputRoot 'docs/SECRET.md' $secret
$target = Join-Path $testRoot 'credential/output'
$failure = Expect-ExportFailure $inputRoot $target 'Credential-like material rejected'
Assert-Export (-not $failure.Contains($secret)) 'diagnostic does not print credential'
Assert-Export (-not (Test-Path -LiteralPath $target)) 'preflight credential refusal creates no output'
Pass 'credential signature rejection without disclosure'

$inputRoot = New-TestSource 'binary'
$file = Join-Path $inputRoot 'legacy-converter/src/main/resources/foreign.jar'
[void][IO.Directory]::CreateDirectory((Split-Path -Parent $file))
[IO.File]::WriteAllBytes($file, [byte[]]@(80, 75, 0, 1))
[void](Expect-ExportFailure $inputRoot (Join-Path $testRoot 'binary/output') 'Unreviewed source-file type/path')
Pass 'unreviewed source archive rejection'

$inputRoot = New-TestSource 'utf8'
$file = Join-Path $inputRoot 'README.md'
[IO.File]::WriteAllBytes($file, [byte[]]@(255, 254, 255))
[void](Expect-ExportFailure $inputRoot (Join-Path $testRoot 'utf8/output') 'valid UTF-8')
Pass 'binary disguised as text rejection'

$inputRoot = New-TestSource 'nul-byte-case'
Write-TestText $inputRoot 'README.md' ('prefix' + [char]0 + 'suffix')
[void](Expect-ExportFailure $inputRoot (Join-Path $testRoot 'nul-byte-case/output') 'NUL/binary')
Pass 'NUL rejection'

$inputRoot = New-TestSource 'wrapper'
[IO.File]::AppendAllText((Join-Path $inputRoot 'gradle/wrapper/gradle-wrapper.jar'), 'modified', $utf8)
[void](Expect-ExportFailure $inputRoot (Join-Path $testRoot 'wrapper/output') 'Gradle wrapper differs')
Pass 'modified wrapper rejected'

$inputRoot = New-TestSource 'bounded'
[IO.File]::WriteAllBytes((Join-Path $inputRoot 'README.md'), [byte[]]::new(4MB + 1))
[void](Expect-ExportFailure $inputRoot (Join-Path $testRoot 'bounded/output') '4 MiB bound')
Pass 'per-file size bound'

$inputRoot = New-TestSource 'missing'
# Rename a test-owned required input without deleting it; the exporter must fail closed.
Move-Item -LiteralPath (Join-Path $inputRoot 'gradle.properties') -Destination (Join-Path $inputRoot 'not-selected.properties')
[void](Expect-ExportFailure $inputRoot (Join-Path $testRoot 'missing/output') 'Required source-build file absent')
Pass 'missing required build input'

$inputRoot = New-TestSource 'java-path'
Write-TestText $inputRoot 'legacy-converter/src/main/java/test/Example.java' ('// actual private root: ' + $inputRoot + '/build/private')
[void](Expect-ExportFailure $inputRoot (Join-Path $testRoot 'java-path/output') 'Unexpected actual private root in Java')
Pass 'actual personal Java path requires manual review rather than changing semantics'

$inputRoot = New-TestSource 'additional-root'
$private = 'C:/Users/' + 'unreviewed-owner'
Write-TestText $inputRoot 'README.md' ($private + '/private/metadata')
$target = Join-Path $testRoot 'additional-root/output'
[void](Expect-ExportFailure $inputRoot $target 'Unreviewed personal path remains')
& $export -SourceRoot $inputRoot -Destination $target -AdditionalPrivateRoots @($private) | Out-Null
Assert-Export ((Get-Content -LiteralPath (Join-Path $target 'README.md') -Raw) -eq '${PRIVATE_LOCAL_ROOT}/private/metadata') 'explicit additional root redacted'
Pass 'unknown personal root rejected until explicitly reviewed'

$inputRoot = New-TestSource 'java8'
$profile = [Environment]::GetFolderPath('UserProfile').Replace('\', '/')
Write-TestText $inputRoot 'scripts/Test-LogicRuntime.ps1' ('$java=''' + $profile + '/.gradle/jdks/reviewed/bin/java.exe''')
$target = Join-Path $testRoot 'java8/output'
& $export -SourceRoot $inputRoot -Destination $target | Out-Null
$script = Get-Content -LiteralPath (Join-Path $target 'scripts/Test-LogicRuntime.ps1') -Raw
Assert-Export ($script.Contains('$env:JAVA8_HOME') -and -not $script.Contains($profile)) 'explicit portable Java 8 configuration'
$tokens = $null; $errors = $null
[void][Management.Automation.Language.Parser]::ParseInput($script, [ref]$tokens, [ref]$errors)
Assert-Export (@($errors).Count -eq 0) 'rewritten QA script parses without execution'
Pass 'native QA Java 8 path becomes explicit configuration only in snapshot'

$inputRoot = New-TestSource 'unsafe-destination'
[void](Expect-ExportFailure $inputRoot (Join-Path $inputRoot 'docs/published') 'inside the checkout')
Pass 'destination cannot pollute source tree'

$inputRoot = New-TestSource 'link'
$link = Join-Path $inputRoot 'docs/linked'
$outside = Join-Path $testRoot 'link/external'
[void][IO.Directory]::CreateDirectory((Split-Path -Parent $link))
[void][IO.Directory]::CreateDirectory($outside)
$linkCreated = $false
try {
    New-Item -ItemType Junction -Path $link -Target $outside -ErrorAction Stop | Out-Null
    $linkCreated = $true
} catch { $skipped++; Write-Output 'SKIP reparse-point test: platform cannot create a junction.' }
if ($linkCreated) {
    [void](Expect-ExportFailure $inputRoot (Join-Path $testRoot 'link/output') 'Reparse point rejected')
    Pass 'selected reparse-point tree rejected'
}

[pscustomobject]@{status='PASS'; passed=$passed; skipped=$skipped; gamesRun=$false; networkUsed=$false; evidenceDirectory=$testRoot}

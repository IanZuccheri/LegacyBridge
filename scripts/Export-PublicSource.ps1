# Creates a NEW, bounded source snapshot. Never publishes, downloads or runs builds/games.
# Machine-path redaction affects the snapshot only; original evidence and inputs stay untouched.
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$Destination,
    [string]$SourceRoot = (Split-Path -Parent $PSScriptRoot),
    [string[]]$AdditionalPrivateRoots = @()
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$utf8 = [Text.UTF8Encoding]::new($false, $true)
$source = [IO.Path]::GetFullPath($SourceRoot).TrimEnd('\', '/')
$destinationRoot = [IO.Path]::GetFullPath($Destination).TrimEnd('\', '/')
$separator = [IO.Path]::DirectorySeparatorChar
$manifestPath = Join-Path (Split-Path -Parent $destinationRoot) ((Split-Path -Leaf $destinationRoot) + '-export-manifest.json')
$manifestPending = $manifestPath + '.partial'
$excludedSegments = @('.git', '.gradle', 'build', 'bin', 'run', 'runs', 'logs', 'local-private', '.idea', 'out', 'node_modules', '.vs')
$modules = @('legacy-compat-api', 'legacy-converter', 'legacy-corpus', 'legacy-product', 'legacy-runtime', 'legacy-workflow')
$topDirectories = $modules + @('fixtures', 'docs', 'corpus', 'scripts', 'tools', 'gradle', '.github', 'licenses')
$rootFiles = @('.gitattributes', '.gitignore', 'LICENSE', 'NOTICE', 'README.md', 'ARCHITECTURE.md', 'COMPATIBILITY.md',
    'CURSEFORGE.md', 'VALIDATION.md', 'CONTRIBUTING.md', 'SECURITY.md', 'CHANGELOG.md', 'BUILDING.md', 'LICENSING.md',
    'LINKING_EXCEPTION.md', 'THIRD_PARTY_NOTICES.md', 'SUMMARY_PT_BR.md',
    'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat')
$sourceExtensions = @('.java', '.json', '.md', '.txt', '.tsv', '.toml', '.properties', '.mcmeta')
$wrapperHash = 'cb0da6751c2b753a16ac168bb354870ebb1e162e9083f116729cec9c781156b8'
$portableJava8Scripts = @('scripts/Test-LogicRuntime.ps1', 'scripts/Test-LifecycleRuntime.ps1',
    'scripts/Test-FoodRuntime.ps1', 'scripts/Test-DatapackRuntime.ps1')
$entries = [Collections.Generic.List[object]]::new()
$pruned = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
$totalBytes = 0L

function Assert-PlainPath([string]$Path) {
    $cursor = [IO.Path]::GetFullPath($Path)
    while ($cursor) {
        if (Test-Path -LiteralPath $cursor) {
            $entry = Get-Item -LiteralPath $cursor -Force
            if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Source export refuses a symlink/junction/reparse-point path.'
            }
        }
        $parent = Split-Path -Parent $cursor
        if ($parent -eq $cursor) { break }
        $cursor = $parent
    }
}
function Get-Sha256([byte[]]$Bytes) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $algorithm.Dispose() }
}
function Assert-NoCredential([string]$Text, [string]$Relative) {
    # Conservative signature checks, not a claim that arbitrary secrets can be recognized.
    $patterns = [ordered]@{
        PRIVATE_KEY = '-----BEGIN (?:RSA |OPENSSH |EC |DSA |ENCRYPTED )?PRIVATE KEY-----'
        HOSTED_TOKEN = '(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})'
        ASSIGNED_CREDENTIAL = '(?im)(?:access[_-]?token|refresh[_-]?token|api[_-]?key|client[_-]?secret|password)\s*[=:]\s*["''][A-Za-z0-9_+/.=-]{24,}["'']'
    }
    foreach ($pair in $patterns.GetEnumerator()) {
        if ([regex]::IsMatch($Text, $pair.Value)) { throw "Credential-like material rejected ($($pair.Key)): $Relative" }
    }
}
function Get-Policy([string]$Relative) {
    $parts = $Relative.Split('/')
    $extension = [IO.Path]::GetExtension($Relative).ToLowerInvariant()
    if ($parts.Count -eq 1) {
        if ($rootFiles -contains $Relative) { return 'ALLOW' }
        return 'EXCLUDE'
    }
    if ($Relative -eq 'gradle/wrapper/gradle-wrapper.jar') { return 'WRAPPER' }
    if ($Relative -eq 'gradle/wrapper/gradle-wrapper.properties') { return 'ALLOW' }
    if ($Relative -eq 'licenses/Apache-2.0.txt') { return 'ALLOW' }
    if ($Relative -eq 'corpus/.gitattributes') { return 'ALLOW' }
    if ($Relative -in @('corpus/licenses/.gitattributes', 'docs/evidence/logic-preserving-v1/.gitattributes',
            'legacy-runtime/.gitignore', 'fixtures/simple-content-1.16.5/.gitignore')) { return 'ALLOW' }
    if ($Relative -eq 'corpus/licenses/legacybridge/legacy-license-2026-09-06.txt') { return 'ALLOW' }
    if ($modules -contains $parts[0]) {
        if ($parts.Count -eq 2 -and $parts[1] -eq 'build.gradle') { return 'ALLOW' }
        if ($parts[1] -eq 'src') {
            if ($sourceExtensions -contains $extension) { return 'ALLOW' }
            return 'REJECT'
        }
        return 'EXCLUDE'
    }
    switch ($parts[0]) {
        'fixtures' {
            if ($Relative -eq 'fixtures/README.md') { return 'ALLOW' }
            if ($Relative -eq 'fixtures/lifecycle-suite-api/BrigadierSuiteProbe.java') { return 'ALLOW' }
            if ($parts.Count -eq 3 -and $parts[2] -in @('build.gradle', 'settings.gradle', 'gradle.properties', 'README.md', 'LICENSE', 'LICENSE.txt')) { return 'ALLOW' }
            if ($parts.Count -ge 4 -and $parts[2] -in @('src', 'META-INF')) {
                if ($sourceExtensions -contains $extension) { return 'ALLOW' }
                return 'REJECT'
            }
        }
        'docs' {
            if ($extension -in @('.md', '.json')) { return 'ALLOW' }
            return 'REJECT'
        }
        'corpus' {
            if ($extension -in @('.md', '.json') -or ($parts[1] -eq 'licenses' -and $parts[-1] -match '^LICENSE(?:\.(?:txt|md))?$')) { return 'ALLOW' }
            return 'REJECT'
        }
        'scripts' {
            if ($parts.Count -eq 2 -and $extension -in @('.ps1', '.java', '.cs', '.md')) { return 'ALLOW' }
            return 'REJECT'
        }
        'tools' {
            if ($parts.Count -eq 2 -and $extension -eq '.java') { return 'ALLOW' }
            return 'REJECT'
        }
        '.github' {
            if ($extension -in @('.md', '.yml', '.yaml') -and $parts.Count -le 3) { return 'ALLOW' }
            return 'REJECT'
        }
    }
    return 'EXCLUDE'
}

Assert-PlainPath $source
Assert-PlainPath $destinationRoot
if (-not (Test-Path -LiteralPath $source -PathType Container)) { throw 'Source root must be an existing plain directory.' }
if ($destinationRoot -eq $source -or $destinationRoot.StartsWith($source + $separator, [StringComparison]::OrdinalIgnoreCase) -and
        -not $destinationRoot.StartsWith((Join-Path $source 'build') + $separator, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'A destination inside the checkout must be under its excluded build directory.'
}
if (Test-Path -LiteralPath $destinationRoot) { throw 'Destination already exists; source export never overwrites or merges.' }
foreach ($path in @($manifestPath, $manifestPending)) {
    Assert-PlainPath $path
    if (Test-Path -LiteralPath $path) { throw 'Export receipt already exists; choose a new destination.' }
}

# Variants match only explicitly private roots, not arbitrary C:/test traversal fixtures.
# URLs and JSON-escaped separators are handled as literal variants of those same roots.
$privateRoots = [Collections.Generic.List[object]]::new()
$privateRoots.Add(@{path=$source; label='${LEGACYBRIDGE_SOURCE_ROOT}'})
$profileRoot = [Environment]::GetFolderPath('UserProfile')
if ($profileRoot) { $privateRoots.Add(@{path=$profileRoot; label='${DEVELOPER_HOME}'}) }
foreach ($path in $AdditionalPrivateRoots) {
    if ([string]::IsNullOrWhiteSpace($path) -or -not [IO.Path]::IsPathRooted($path)) { throw 'Additional private roots must be explicit absolute paths.' }
    $normalized = [IO.Path]::GetFullPath($path).TrimEnd('\', '/')
    if ($normalized -eq [IO.Path]::GetPathRoot($normalized).TrimEnd('\', '/')) { throw 'A whole drive is not a private root substitution.' }
    $privateRoots.Add(@{path=$normalized; label='${PRIVATE_LOCAL_ROOT}'})
}
function Redact-Roots([string]$Text, [string]$Relative, $Changes) {
    $result = $Text
    foreach ($root in ($privateRoots | Sort-Object { $_.path.Length } -Descending)) {
        $forward = $root.path.Replace('\', '/')
        $backward = $root.path.Replace('/', '\')
        $variants = @($forward, $backward, $backward.Replace('\', '\\'), $forward.Replace(' ', '%20'), $backward.Replace(' ', '%20')) | Select-Object -Unique
        foreach ($variant in $variants) {
            # A boundary prevents redacting a different directory that shares only a prefix.
            $pattern = [regex]::Escape($variant) + '(?=$|[\\/"''\s`<>])'
            if ([regex]::IsMatch($result, $pattern, [Text.RegularExpressions.RegexOptions]::IgnoreCase)) {
                if ($Relative.EndsWith('.java', [StringComparison]::OrdinalIgnoreCase)) {
                    throw "Unexpected actual private root in Java source; review manually: $Relative"
                }
                $label = [string]$root.label
                $result = [regex]::Replace($result, $pattern, [Text.RegularExpressions.MatchEvaluator]{param($m) $label}, [Text.RegularExpressions.RegexOptions]::IgnoreCase)
                if (-not $Changes.Contains('PRIVATE_ROOT_PLACEHOLDER')) { $Changes.Add('PRIVATE_ROOT_PLACEHOLDER') }
            }
        }
    }
    # Unreviewed personal roots are not silently shipped; synthetic test paths remain untouched.
    if (-not $Relative.EndsWith('.java', [StringComparison]::OrdinalIgnoreCase) -and
        [regex]::IsMatch($result, '(?i)[A-Z]:[\\/]+Users[\\/]+[^\\/\s"''<>]+')) {
        throw "Unreviewed personal path remains; use AdditionalPrivateRoots after review: $Relative"
    }
    return $result
}
function Add-SourceFile($File, [string]$Relative, [string]$Policy) {
    if ($File.Length -gt 4MB) { throw "Source file exceeds the 4 MiB bound: $Relative" }
    $bytes = [IO.File]::ReadAllBytes($File.FullName)
    if ($bytes.Length -gt 4MB) { throw "Source file grew beyond the bound: $Relative" }
    $sourceHash = Get-Sha256 $bytes
    $changes = [Collections.Generic.List[string]]::new()
    if ($Policy -eq 'WRAPPER') {
        if ($sourceHash -ne $wrapperHash) { throw 'Gradle wrapper differs from the reviewed exact wrapper; audit before export.' }
        $result = $bytes
    } else {
        try { $text = $utf8.GetString($bytes) }
        catch { throw "Source must be valid UTF-8, not an unreviewed binary: $Relative" }
        if ($text.Contains([char]0)) { throw "NUL/binary source rejected: $Relative" }
        Assert-NoCredential $text $Relative
        if ($portableJava8Scripts -contains $Relative) {
            $pattern = '(?m)^(?<indent>[ \t]*)\$java[ \t]*=[ \t]*''[A-Z]:[/\\]Users[/\\][^''\r\n]+[/\\]bin[/\\]java\.exe''[ \t]*\r?$'
            if ([regex]::IsMatch($text, $pattern)) {
                $text = [regex]::Replace($text, $pattern, [Text.RegularExpressions.MatchEvaluator]{ param($m)
                    $indent = $m.Groups['indent'].Value
                    $indent + 'if ([string]::IsNullOrWhiteSpace($env:JAVA8_HOME)) { throw ''Set JAVA8_HOME to your reviewed Java 8 installation before native QA.'' }' + "`n" +
                        $indent + '$java = Join-Path $env:JAVA8_HOME ''bin/java.exe'''
                })
                $changes.Add('JAVA8_HOME_EXPLICIT_CONFIGURATION')
            }
        }
        $cleaned = Redact-Roots $text $Relative $changes
        if ($cleaned -ceq $utf8.GetString($bytes)) { $result = $bytes }
        else { $result = $utf8.GetBytes($cleaned) }
        Assert-NoCredential $cleaned $Relative
    }
    $script:totalBytes += $result.Length
    if ($script:totalBytes -gt 64MB -or $entries.Count -ge 5000) { throw 'Source snapshot exceeds the 64 MiB / 5000-file bound.' }
    $entries.Add([pscustomobject]@{relative=$Relative; original=$File.FullName; bytes=$result; sourceSha256=$sourceHash;
        sha256=(Get-Sha256 $result); sizeBytes=$result.Length; transformations=@($changes.ToArray())})
}
function Walk-Source([string]$Directory, [string]$Prefix) {
    foreach ($entry in (Get-ChildItem -LiteralPath $Directory -Force | Sort-Object Name)) {
        $relative = if ($Prefix) { $Prefix + '/' + $entry.Name } else { $entry.Name }
        if ($entry.PSIsContainer -and $excludedSegments -contains $entry.Name) {
            [void]$pruned.Add($entry.Name); continue
        }
        if (-not $Prefix -and $entry.PSIsContainer -and $topDirectories -notcontains $entry.Name) {
            [void]$pruned.Add('UNLISTED_ROOT_DIRECTORY'); continue
        }
        if (($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw "Reparse point rejected in selected tree: $relative" }
        if ($entry.PSIsContainer) { Walk-Source $entry.FullName $relative; continue }
        $policy = Get-Policy $relative
        if ($policy -eq 'EXCLUDE') { [void]$pruned.Add('UNLISTED_FILE'); continue }
        if ($policy -eq 'REJECT') { throw "Unreviewed source-file type/path rejected: $relative" }
        Add-SourceFile $entry $relative $policy
    }
}
Walk-Source $source ''
$required = @('LICENSE', 'README.md', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat',
    'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties') + @($modules | ForEach-Object { $_ + '/build.gradle' })
foreach ($relative in $required) {
    if ($relative -notin $entries.relative) { throw "Required source-build file absent from snapshot: $relative" }
}

# All files are scanned before destination creation. Preflight failures leave no snapshot.
# A later I/O failure deliberately leaves a non-publishable partial tree WITHOUT a final receipt.
# There is no automatic cleanup, overwrite, Git action or claim of cross-file atomicity.
[void][IO.Directory]::CreateDirectory((Split-Path -Parent $destinationRoot))
Assert-PlainPath $destinationRoot
if (Test-Path -LiteralPath $destinationRoot) { throw 'Destination appeared during preflight; refusing to merge.' }
[void][IO.Directory]::CreateDirectory($destinationRoot)
foreach ($entry in $entries) {
    Assert-PlainPath $entry.original
    if ((Get-FileHash -LiteralPath $entry.original -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sourceSha256) {
        throw "Source changed after preflight; partial export must not be published: $($entry.relative)"
    }
    $target = Join-Path $destinationRoot $entry.relative
    Assert-PlainPath $target
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $target))
    $stream = [IO.File]::Open($target, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($entry.bytes, 0, $entry.bytes.Length); $stream.Flush($true) }
    finally { $stream.Dispose() }
    if ((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) {
        throw "Snapshot readback hash mismatch: $($entry.relative)"
    }
}
$receipt = [ordered]@{
    schemaVersion=1; status='SOURCE_EXPORT_COMPLETE_NOT_PUBLISHED'; createdAtUtc=[DateTime]::UtcNow.ToString('o');
    sourceIdentity='LegacyBridge working checkout'; snapshotName=(Split-Path -Leaf $destinationRoot);
    fileCount=$entries.Count; totalBytes=$totalBytes; wrapperSha256=$wrapperHash;
    redactionPolicy='Explicit source/home/private roots only; Java semantic test paths not rewritten; four native QA scripts use explicit JAVA8_HOME.';
    excludedCategories=@($pruned | Sort-Object);
    limitations=@('Signature-based credential detection is not exhaustive; human review is required before public push.',
        'No build, tests, native validation, rights clearance, Git action or publication was performed by this export.',
        'Historical ledger paths are redacted metadata; hashes still identify the original historical artifacts, not new runtime evidence.',
        'Full local mapping/API bundles, third-party mod binaries, native installs, logs and screenshots are intentionally absent.');
    files=@($entries | ForEach-Object { [ordered]@{path=$_.relative; sourceSha256=$_.sourceSha256; sha256=$_.sha256; sizeBytes=$_.sizeBytes; transformations=$_.transformations} })
}
$receiptBytes = $utf8.GetBytes(($receipt | ConvertTo-Json -Depth 8) + "`n")
$stream = [IO.File]::Open($manifestPending, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
try { $stream.Write($receiptBytes, 0, $receiptBytes.Length); $stream.Flush($true) }
finally { $stream.Dispose() }
[IO.File]::Move($manifestPending, $manifestPath)
[pscustomobject]@{status=$receipt.status; fileCount=$entries.Count; totalBytes=$totalBytes; manifest=$manifestPath}

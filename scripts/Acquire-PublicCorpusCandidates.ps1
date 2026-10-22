[CmdletBinding()]
param([switch]$AcceptReviewedLicenses)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if (-not $AcceptReviewedLicenses) {
    throw 'Read corpus/intake/rights-candidates.json and the pinned license URLs, then explicitly pass -AcceptReviewedLicenses. No download was started.'
}
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$catalog = Get-Content -LiteralPath (Join-Path $repository 'corpus/intake/rights-candidates.json') -Raw | ConvertFrom-Json
Add-Type -AssemblyName System.Net.Http
$handler = [Net.Http.HttpClientHandler]::new()
$handler.AllowAutoRedirect = $false
$http = [Net.Http.HttpClient]::new($handler)
$http.Timeout = [TimeSpan]::FromSeconds(60)
$http.DefaultRequestHeaders.UserAgent.ParseAdd('LegacyBridge-corpus-intake/1.0')

function Get-BoundedBytes([string]$Address, [int]$Maximum, [string]$RequiredHost) {
    $uri = [Uri]$Address
    if ($uri.Scheme -ne 'https' -or $uri.DnsSafeHost -ne $RequiredHost -or $uri.UserInfo -ne '') {
        throw 'Unexpected public acquisition host or credentials.'
    }
    $response = $http.GetAsync($uri, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    try {
        $response.EnsureSuccessStatusCode() | Out-Null
        if ($response.Content.Headers.ContentLength -gt $Maximum) { throw 'Response exceeds the byte budget.' }
        $stream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
        $memory = [IO.MemoryStream]::new()
        try {
            $buffer = New-Object byte[] 8192
            while (($count = $stream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                if ($memory.Length + $count -gt $Maximum) { throw 'Response exceeded the streaming byte budget.' }
                $memory.Write($buffer, 0, $count)
            }
            return ,$memory.ToArray()
        } finally { $memory.Dispose(); $stream.Dispose() }
    } finally { $response.Dispose() }
}

function Get-ByteHash([byte[]]$Bytes, [string]$Algorithm) {
    $digest = [Security.Cryptography.HashAlgorithm]::Create($Algorithm)
    try { return ([BitConverter]::ToString($digest.ComputeHash($Bytes))).Replace('-', '').ToLowerInvariant() }
    finally { $digest.Dispose() }
}

function Ensure-OwnedDirectory([string]$Path) {
    $absolute = [IO.Path]::GetFullPath($Path)
    if (-not $absolute.StartsWith($repository + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Acquisition path escaped the repository.'
    }
    $relative = $absolute.Substring($repository.Length + 1)
    $cursor = $repository
    foreach ($segment in $relative.Split([IO.Path]::DirectorySeparatorChar)) {
        $cursor = Join-Path $cursor $segment
        if (-not (Test-Path -LiteralPath $cursor)) { [IO.Directory]::CreateDirectory($cursor) | Out-Null }
        $item = Get-Item -LiteralPath $cursor -Force
        if (-not $item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'Acquisition path contains a non-directory or reparse point.'
        }
    }
}

function Write-NewBytes([string]$Path, [byte[]]$Bytes) {
    $output = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $output.Write($Bytes, 0, $Bytes.Length); $output.Flush($true) }
    finally { $output.Dispose() }
}

try {
    foreach ($candidate in $catalog.candidates) {
        if ($candidate.rights.status -ne 'LOCAL_ACQUISITION_STATIC_ANALYSIS_MODIFICATION_ALLOWED') { throw 'Unreviewed rights status.' }
        if ($candidate.slug -notmatch '^[a-z0-9-]+$' -or $candidate.artifact.filename -notmatch '^[A-Za-z0-9.+_-]+\.jar$') {
            throw 'Unsafe candidate slug or filename.'
        }
        $directory = Join-Path $repository ('build/corpus-intake/public/' + $candidate.slug)
        Ensure-OwnedDirectory $directory
        $destination = Join-Path $directory $candidate.artifact.filename
        $receiptPath = Join-Path $directory 'acquisition.json'
        $licensePath = Join-Path $directory 'UPSTREAM-LICENSE.txt'
        foreach ($output in @($destination, $receiptPath, $licensePath)) {
            if (Test-Path -LiteralPath $output) { throw ('Refusing to overwrite existing acquisition output: ' + $output) }
        }
        # The immutable release-associated license is verified BEFORE fetching binary bytes.
        $licenseBytes = Get-BoundedBytes $candidate.rights.licenseUrl 65536 'raw.githubusercontent.com'
        if ((Get-ByteHash $licenseBytes 'SHA256') -ne $candidate.rights.licenseSha256) { throw 'Pinned license hash mismatch.' }
        $jarBytes = Get-BoundedBytes $candidate.artifact.downloadUrl 10485760 'cdn.modrinth.com'
        $jarHash = Get-ByteHash $jarBytes 'SHA256'
        if ($jarBytes.Length -ne $candidate.artifact.sizeBytes -or
            (Get-ByteHash $jarBytes 'SHA512') -ne $candidate.artifact.publisherSha512) {
            throw 'Publisher size or SHA-512 mismatch; no JAR was written.'
        }
        if ($candidate.artifact.sha256 -and $jarHash -ne $candidate.artifact.sha256) { throw 'Locked SHA-256 mismatch.' }
        Write-NewBytes $licensePath $licenseBytes
        Write-NewBytes $destination $jarBytes
        $receipt = [ordered]@{
            schemaVersion = 1
            acquiredAtUtc = [DateTime]::UtcNow.ToString('o')
            candidateId = $candidate.id
            projectId = $candidate.publisher.projectId
            versionId = $candidate.publisher.versionId
            filename = $candidate.artifact.filename
            sizeBytes = $jarBytes.Length
            sha256 = $jarHash
            sha512 = Get-ByteHash $jarBytes 'SHA512'
            downloadUrl = $candidate.artifact.downloadUrl
            licenseUrl = $candidate.rights.licenseUrl
            licenseSha256 = Get-ByteHash $licenseBytes 'SHA256'
            sourceCommit = $candidate.rights.sourceCommit
            originalCodeExecuted = $false
            localOnly = $true
            redistributionByThisOperation = $false
        }
        $json = $receipt | ConvertTo-Json -Depth 6
        Write-NewBytes $receiptPath ([Text.UTF8Encoding]::new($false).GetBytes($json + [Environment]::NewLine))
        $json
    }
} finally { $http.Dispose(); $handler.Dispose() }

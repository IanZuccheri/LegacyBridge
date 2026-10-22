[CmdletBinding()]
param(
    [ValidateSet('p3-production', 'p4-ui', 'p5-transactions', 'p6-public', 'embedded-simple')][string]$ValidationRun = 'p3-production',
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17',
    [string]$VanillaDirectory = (Join-Path $env:APPDATA '.minecraft'),
    [switch]$AllowDownloads,
    [switch]$RunInstaller
)
. (Join-Path $PSScriptRoot 'ProductionClient.Common.ps1')
$clientDirectory = Assert-ProductionRoot $productionRoot
New-Item -ItemType Directory -Path $clientDirectory -Force | Out-Null
$vanillaDirectory = (Resolve-Path -LiteralPath $VanillaDirectory).Path
$copied = 0
$downloaded = 0
$verified = 0
$installer = Join-Path $productionProject 'build/p3-production/setup/forge-1.20.1-47.4.23-installer.jar'
$installerSha256 = '75cfcb11f60cc641dc83757c1357c08c9bdba255ec529feb115c5e89a59cf75a'
if (-not (Test-Path -LiteralPath $installer -PathType Leaf) -or
        (Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash -ne $installerSha256) {
    throw 'Provide the pinned official Forge installer in build/p3-production/setup; it is not downloaded implicitly.'
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$installerArchive = [IO.Compression.ZipFile]::OpenRead($installer)
try {
    $profileEntry = $installerArchive.GetEntry('install_profile.json')
    if (-not $profileEntry -or $profileEntry.Length -gt 1048576) { throw 'Invalid bounded Forge installation metadata.' }
    $profileReader = [IO.StreamReader]::new($profileEntry.Open())
    try { $installProfile = $profileReader.ReadToEnd() | ConvertFrom-Json }
    finally { $profileReader.Dispose() }
} finally { $installerArchive.Dispose() }

function Install-VerifiedArtifact([string]$Target, [string]$Source, [string]$Sha1, [string]$Url) {
    if (Test-Path -LiteralPath $Target) { Assert-ProductionHash $Target $Sha1; $script:verified++; return }
    New-Item -ItemType Directory -Path (Split-Path -Parent $Target) -Force | Out-Null
    if ($Source -and (Test-Path -LiteralPath $Source -PathType Leaf) -and
            (Get-FileHash -LiteralPath $Source -Algorithm SHA1).Hash -eq $Sha1) {
        Copy-Item -LiteralPath $Source -Destination $Target -ErrorAction Stop
        $script:copied++
    } else {
        if (-not $AllowDownloads) { throw "Missing verified local artifact; explicitly use -AllowDownloads to fetch: $Url" }
        $uri = [Uri]$Url
        if ($uri.Scheme -ne 'https' -or $uri.Host -notin @('piston-meta.mojang.com', 'piston-data.mojang.com',
                'libraries.minecraft.net', 'resources.download.minecraft.net', 'maven.minecraftforge.net')) {
            throw "Artifact URL is not in the official validation allowlist: $Url"
        }
        Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Target
        $script:downloaded++
    }
    Assert-ProductionHash $Target $Sha1
}

# Pin the metadata actually verified from Mojang's version_manifest_v2 on 2026-09-05.
$vanillaMetadataSha1 = '19f5ae58f9c31bd3b0923cb822e99e3162bd62ab'
$vanillaMetadata = Get-ProductionChild $clientDirectory 'versions/1.20.1/1.20.1.json'
Install-VerifiedArtifact $vanillaMetadata (Join-Path $vanillaDirectory 'versions/1.20.1/1.20.1.json') $vanillaMetadataSha1 `
    "https://piston-meta.mojang.com/v1/packages/$vanillaMetadataSha1/1.20.1.json"
$vanilla = Get-Content -LiteralPath $vanillaMetadata -Raw | ConvertFrom-Json
$vanillaJar = Get-ProductionChild $clientDirectory 'versions/1.20.1/1.20.1.jar'
Install-VerifiedArtifact $vanillaJar (Join-Path $vanillaDirectory 'versions/1.20.1/1.20.1.jar') `
    $vanilla.downloads.client.sha1 $vanilla.downloads.client.url

if ($RunInstaller) {
    if (-not $AllowDownloads) { throw 'Forge installer performs network requests: explicitly pass -AllowDownloads.' }
    $profiles = Join-Path $clientDirectory 'launcher_profiles.json'
    if (-not (Test-Path -LiteralPath $profiles)) {
        [IO.File]::WriteAllText($profiles, '{"profiles":{}}', [Text.UTF8Encoding]::new($false))
    }
    $java = Join-Path $JavaHome 'bin/java.exe'
    Push-Location -LiteralPath $clientDirectory
    try {
        & $java '-Djavax.net.ssl.trustStore=NONE' '-Djavax.net.ssl.trustStoreType=Windows-ROOT' '-jar' $installer '--installClient' $clientDirectory
        if ($LASTEXITCODE -ne 0) { throw "Forge client installer failed: $LASTEXITCODE" }
    } finally { Pop-Location }
}

$forgeMetadata = Get-ProductionChild $clientDirectory "versions/$productionVersionId/$productionVersionId.json"
if (-not (Test-Path -LiteralPath $forgeMetadata)) { throw 'Run the official installer explicitly using -RunInstaller -AllowDownloads.' }
$forge = Get-Content -LiteralPath $forgeMetadata -Raw | ConvertFrom-Json
if ($forge.inheritsFrom -ne '1.20.1' -or $forge.id -ne $productionVersionId) { throw 'Unexpected installed Forge profile.' }
foreach ($library in (Get-ProductionLibraries $vanilla $forge)) {
    $artifact = $library.downloads.artifact
    if (-not $artifact.path) { throw "Missing normal-launcher download metadata: $($library.name)" }
    $target = Get-ProductionChild (Join-Path $clientDirectory 'libraries') $artifact.path
    Install-VerifiedArtifact $target (Join-Path (Join-Path $vanillaDirectory 'libraries') $artifact.path) $artifact.sha1 $artifact.url
}

# These are discovered by FML from the library directory, not the bootstrap
# classpath. Their official hashes are in install_profile.json, not version.json.
$runtimeLibraries = @('fmlcore', 'javafmllanguage', 'lowcodelanguage', 'mclanguage', 'forge')
foreach ($name in $runtimeLibraries) {
    $coordinate = 'net.minecraftforge:' + $name + ':1.20.1-47.4.23'
    if ($name -eq 'forge') { $coordinate += ':universal' }
    $matches = @($installProfile.libraries | Where-Object { $_.name -eq $coordinate })
    if ($matches.Count -ne 1) { throw "Missing pinned installer runtime library: $coordinate" }
    $artifact = $matches[0].downloads.artifact
    Install-VerifiedArtifact (Get-ProductionChild (Join-Path $clientDirectory 'libraries') $artifact.path) `
        (Join-Path (Join-Path $vanillaDirectory 'libraries') $artifact.path) $artifact.sha1 $artifact.url
}

# Official processor outputs have no download URLs. Reuse an explicitly selected
# installed instance, or require the explicitly network-enabled official installer.
# Extra/patched hashes are pinned by the installer; SRG has no official output hash
# there, so its locally verified provenance is recorded separately, never invented.
$processorPaths = [ordered]@{
    MC_EXTRA = 'libraries/net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-extra.jar'
    MC_SRG = 'libraries/net/minecraft/client/1.20.1-20230612.114412/client-1.20.1-20230612.114412-srg.jar'
    PATCHED = 'libraries/net/minecraftforge/forge/1.20.1-47.4.23/forge-1.20.1-47.4.23-client.jar'
}
$processorOutputs = @()
foreach ($name in $processorPaths.Keys) {
    $relative = $processorPaths[$name]
    $target = Get-ProductionChild $clientDirectory $relative
    $source = Join-Path $vanillaDirectory $relative
    if (-not (Test-Path -LiteralPath $target -PathType Leaf)) {
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
            throw "Missing Forge processor output: $relative. Select an installed Forge instance with -VanillaDirectory, or use -RunInstaller -AllowDownloads."
        }
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        Copy-Item -LiteralPath $source -Destination $target -ErrorAction Stop
        $copied++
    }
    $digest = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($name -ne 'MC_SRG') {
        $officialSha1 = $installProfile.data.($name + '_SHA').client.Trim("'")
        Assert-ProductionHash $target $officialSha1
        $provenance = 'PINNED_OFFICIAL_INSTALLER_OUTPUT_SHA1'
    } elseif ($RunInstaller) {
        $provenance = 'EXPLICIT_OFFICIAL_INSTALLER_RUN_LOCAL_SHA256'
    } else {
        if (-not (Test-Path -LiteralPath $source -PathType Leaf) -or
                (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne $digest) {
            throw 'SRG output has no official published digest here; require a matching explicitly selected installed source or rerun the installer.'
        }
        $provenance = 'EXPLICIT_INSTALLED_SOURCE_MATCH_LOCAL_SHA256'
    }
    $processorOutputs += [ordered]@{ path = $relative; sha256 = $digest; provenance = $provenance }
}

$indexRelative = 'assets/indexes/' + $vanilla.assetIndex.id + '.json'
$index = Get-ProductionChild $clientDirectory $indexRelative
Install-VerifiedArtifact $index (Join-Path $vanillaDirectory $indexRelative) $vanilla.assetIndex.sha1 $vanilla.assetIndex.url
$assetIndex = Get-Content -LiteralPath $index -Raw | ConvertFrom-Json
$objects = @($assetIndex.objects.PSObject.Properties.Value | Sort-Object hash -Unique)
foreach ($asset in $objects) {
    if ($asset.hash -notmatch '^[0-9a-f]{40}$') { throw 'Invalid asset hash in official index.' }
    $relative = 'assets/objects/' + $asset.hash.Substring(0, 2) + '/' + $asset.hash
    Install-VerifiedArtifact (Get-ProductionChild $clientDirectory $relative) (Join-Path $vanillaDirectory $relative) `
        $asset.hash ('https://resources.download.minecraft.net/' + $asset.hash.Substring(0, 2) + '/' + $asset.hash)
}
foreach ($area in @('mods', 'natives', 'logs', 'legacybridge/input', 'legacybridge/staging', 'legacybridge/reports',
        'legacybridge/cache', 'legacybridge/transactions', 'tmp', 'empty-user-home')) {
    New-Item -ItemType Directory -Path (Get-ProductionChild $clientDirectory $area) -Force | Out-Null
}
$receipt = [ordered]@{
    schemaVersion = 2
    reportType = 'LEGACYBRIDGE_PRODUCTION_CLIENT_PREPARATION'
    minecraft = '1.20.1'
    forge = '47.4.23'
    installerSha256 = $installerSha256
    processorOutputs = $processorOutputs
    vanillaMetadataSha1 = $vanillaMetadataSha1
    vanillaClientSha1 = $vanilla.downloads.client.sha1
    forgeMetadataSha256 = (Get-FileHash -LiteralPath $forgeMetadata -Algorithm SHA256).Hash.ToLowerInvariant()
    assetIndexSha1 = $vanilla.assetIndex.sha1
    assetObjects = $objects.Count
    copied = $copied
    downloaded = $downloaded
    reusedVerified = $verified
    gameLaunched = $false
    credentialsRead = $false
    gradleClasspathUsed = $false
}
[IO.File]::WriteAllText((Join-Path $clientDirectory 'preparation.json'), ($receipt | ConvertTo-Json -Depth 5), [Text.UTF8Encoding]::new($false))
$receipt | ConvertTo-Json -Depth 5

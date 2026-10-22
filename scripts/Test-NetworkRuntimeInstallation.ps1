# Pure installer-inventory/native-option/identity-metadata tests. No Pester, downloads, processes, games or disk output.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
. (Join-Path $PSScriptRoot 'NetworkRuntime.Installation.ps1')
$script:InstallationCaseCount = 0
$script:InstallationAssertionCount = 0
$discoveryNames = @('fmlcore', 'javafmllanguage', 'lowcodelanguage', 'mclanguage', 'forge')

function New-InstallationProfile {
    $libraries = @()
    for ($index = 0; $index -lt $discoveryNames.Count; $index++) {
        $name = $discoveryNames[$index]
        $classifier = if ($name -eq 'forge') { '-universal' } else { '' }
        $coordinate = 'net.minecraftforge:' + $name + ':1.20.1-47.4.23'
        if ($name -eq 'forge') { $coordinate += ':universal' }
        $libraries += [pscustomobject]@{
            name = $coordinate
            downloads = [pscustomobject]@{
                artifact = [pscustomobject]@{
                    path = 'net/minecraftforge/' + $name + '/1.20.1-47.4.23/' + $name + '-1.20.1-47.4.23' + $classifier + '.jar'
                    sha1 = ([string][char](97 + $index)) * 40
                    size = 1024 + $index
                }
            }
        }
    }
    return [pscustomobject]@{
        spec = 1
        minecraft = '1.20.1'
        version = '1.20.1-forge-47.4.23'
        libraries = $libraries
    }
}

function Assert-InstallationEqual([object]$Expected, [object]$Actual, [string]$Context) {
    $script:InstallationAssertionCount++
    if ($Expected -cne $Actual) {
        throw ('Assertion failed: ' + $Context + '; expected=' + $Expected + '; actual=' + $Actual)
    }
}

function Assert-InstallationRejected([scriptblock]$Action, [string]$Context) {
    $script:InstallationAssertionCount++
    $rejected = $false
    try { $null = & $Action } catch { $rejected = $true }
    if (-not $rejected) { throw ('Expected installation refusal: ' + $Context) }
}

function Invoke-InstallationCase([string]$Name, [scriptblock]$Action) {
    try { $null = & $Action } catch {
        throw ('Installation case failed: ' + $Name + '; ' + $_.Exception.Message)
    }
    $script:InstallationCaseCount++
}

function Assert-InstallationInventory($Profile, $Results) {
    Assert-InstallationEqual 5 @($Results).Count 'Exactly five discovery libraries'
    for ($index = 0; $index -lt $discoveryNames.Count; $index++) {
        $expected = $Profile.libraries[$index]
        Assert-InstallationEqual $expected.name $Results[$index].name 'Coordinate and discovery order preserved'
        Assert-InstallationEqual $expected.downloads.artifact.path $Results[$index].path 'Exact installation-relative path preserved'
        Assert-InstallationEqual $expected.downloads.artifact.sha1 $Results[$index].sha1 'Official hash value preserved'
        Assert-InstallationEqual $expected.downloads.artifact.size $Results[$index].size 'Artifact size preserved'
    }
}

Invoke-InstallationCase 'exact-five-artifacts' {
    $profile = New-InstallationProfile
    $results = @(Get-NetworkDiscoveryLibraries $profile)
    Assert-InstallationInventory $profile $results
}
Invoke-InstallationCase 'input-order-does-not-change-discovery-order' {
    $profile = New-InstallationProfile
    $expected = New-InstallationProfile
    [array]::Reverse($profile.libraries)
    Assert-InstallationInventory $expected @(Get-NetworkDiscoveryLibraries $profile)
}
Invoke-InstallationCase 'unrelated-installer-tool-is-not-copied-or-validated-as-runtime' {
    $profile = New-InstallationProfile
    $profile.libraries += [pscustomobject]@{
        name = 'net.minecraftforge:installertools:1.4.1'
        downloads = [pscustomobject]@{ artifact = [pscustomobject]@{ path = 'not-a-runtime-path'; sha1 = 'not-a-runtime-hash'; size = -1 } }
    }
    Assert-InstallationInventory $profile @(Get-NetworkDiscoveryLibraries $profile)
}
Invoke-InstallationCase 'size-inclusive-boundaries-int64-and-integral-double' {
    $profile = New-InstallationProfile
    $profile.libraries[0].downloads.artifact.size = 1
    $profile.libraries[1].downloads.artifact.size = 16777216
    $profile.libraries[2].downloads.artifact.size = [int64]1024
    $profile.libraries[3].downloads.artifact.size = [double]1024
    Assert-InstallationInventory $profile @(Get-NetworkDiscoveryLibraries $profile)
}

for ($index = 0; $index -lt $discoveryNames.Count; $index++) {
    Invoke-InstallationCase ('missing-' + $discoveryNames[$index]) {
        $profile = New-InstallationProfile
        $missing = $profile.libraries[$index].name
        $profile.libraries = @($profile.libraries | Where-Object { $_.name -cne $missing })
        Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'A required discovery library cannot be omitted'
    }
    Invoke-InstallationCase ('duplicate-' + $discoveryNames[$index]) {
        $profile = New-InstallationProfile
        $profile.libraries += $profile.libraries[$index]
        Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'Duplicate exact coordinates cannot overwrite each other'
    }
    Invoke-InstallationCase ('wrong-coordinate-version-' + $discoveryNames[$index]) {
        $profile = New-InstallationProfile
        $profile.libraries[$index].name = $profile.libraries[$index].name.Replace('47.4.23', '47.4.24')
        Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'Another Forge version cannot satisfy the exact coordinate'
    }
    Invoke-InstallationCase ('wrong-path-' + $discoveryNames[$index]) {
        $profile = New-InstallationProfile
        $profile.libraries[$index].downloads.artifact.path += '.unrelated'
        Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'Every discovery artifact requires its own exact path'
    }
}

$baselines = @(
    @{ field = 'spec'; value = 0 },
    @{ field = 'spec'; value = 2 },
    @{ field = 'spec'; value = 'unknown' },
    @{ field = 'minecraft'; value = '1.16.5' },
    @{ field = 'minecraft'; value = '1.20.2' },
    @{ field = 'minecraft'; value = '1.20.1 ' },
    @{ field = 'version'; value = '1.20.1-forge-47.4.22' },
    @{ field = 'version'; value = '1.20.1-forge-47.4.23 ' },
    @{ field = 'version'; value = '1.20.1-FORGE-47.4.23' }
)
for ($index = 0; $index -lt $baselines.Count; $index++) {
    Invoke-InstallationCase ('wrong-baseline-' + $index) {
        $profile = New-InstallationProfile
        $profile.($baselines[$index].field) = $baselines[$index].value
        Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'Exact installer baseline required'
    }
}
foreach ($field in @('spec', 'minecraft', 'version', 'libraries')) {
    Invoke-InstallationCase ('missing-profile-field-' + $field) {
        $profile = New-InstallationProfile
        $profile.PSObject.Properties.Remove($field)
        Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'Missing baseline/inventory fails closed'
    }
}
Invoke-InstallationCase 'null-profile' {
    Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $null } 'A profile is mandatory'
}
Invoke-InstallationCase 'empty-libraries' {
    $profile = New-InstallationProfile
    $profile.libraries = @()
    Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'No discovery inventory is not success'
}

$exactPath = 'net/minecraftforge/fmlcore/1.20.1-47.4.23/fmlcore-1.20.1-47.4.23.jar'
$badPaths = @(
    ('../' + $exactPath),
    ('C:/' + $exactPath),
    ('/' + $exactPath),
    $exactPath.Replace('/', '\'),
    $exactPath.ToUpperInvariant(),
    ($exactPath + ' '),
    ($exactPath + '.bak'),
    ('./' + $exactPath),
    $exactPath.Replace('/fmlcore/', '/alias/../fmlcore/'),
    ('https://example.invalid/' + $exactPath),
    ('%2e%2e/' + $exactPath),
    $exactPath.Replace('fmlcore-1.20.1-47.4.23.jar', 'fmlcore-1.20.1-47.4.23-universal.jar')
)
for ($index = 0; $index -lt $badPaths.Count; $index++) {
    Invoke-InstallationCase ('unsafe-or-mismatched-path-' + $index) {
        $profile = New-InstallationProfile
        $profile.libraries[0].downloads.artifact.path = $badPaths[$index]
        Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'Aliases and path normalization cannot replace the exact official path'
    }
}
$badHashes = @(('A' * 40), ('a' * 39), ('a' * 41), ('g' * 40), ('0x' + ('a' * 40)), (('a' * 40) + ' '), '')
for ($index = 0; $index -lt $badHashes.Count; $index++) {
    Invoke-InstallationCase ('invalid-sha1-' + $index) {
        $profile = New-InstallationProfile
        $profile.libraries[0].downloads.artifact.sha1 = $badHashes[$index]
        Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'SHA1 must be exactly forty lowercase hexadecimal characters'
    }
}
$badSizes = @(0, -1, 16777217, 1.5, '1024', $null, [double]::NaN, [double]::PositiveInfinity)
for ($index = 0; $index -lt $badSizes.Count; $index++) {
    Invoke-InstallationCase ('invalid-size-' + $index) {
        $profile = New-InstallationProfile
        $profile.libraries[0].downloads.artifact.size = $badSizes[$index]
        Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'Size must be a bounded positive integral number, not a coerced string or fraction'
    }
}
foreach ($field in @('path', 'sha1', 'size')) {
    Invoke-InstallationCase ('missing-artifact-field-' + $field) {
        $profile = New-InstallationProfile
        $profile.libraries[0].downloads.artifact.PSObject.Properties.Remove($field)
        Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'Required artifact metadata cannot be guessed'
    }
}
Invoke-InstallationCase 'missing-downloads' {
    $profile = New-InstallationProfile
    $profile.libraries[0].PSObject.Properties.Remove('downloads')
    Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'No downloads metadata fails closed'
}
Invoke-InstallationCase 'missing-artifact' {
    $profile = New-InstallationProfile
    $profile.libraries[0].downloads.PSObject.Properties.Remove('artifact')
    Assert-InstallationRejected { Get-NetworkDiscoveryLibraries $profile } 'No artifact metadata fails closed'
}

# Pinned Options.java accepts render distance >= 2 but simulation distance >= 5.
# The old simulationDistance:2 produced a native validation error and fallback;
# proving the generated text is valid remains separate from a native client run.
$expectedClientOptions = [ordered]@{
    renderDistance = '2'
    simulationDistance = '5'
    maxFps = '20'
    onboardAccessibility = 'false'
    skipMultiplayerWarning = 'true'
    joinedFirstServer = 'true'
    pauseOnLostFocus = 'false'
    enableVsync = 'false'
    fullscreen = 'false'
    graphicsMode = '0'
    particles = '2'
    soundCategory_master = '0.0'
}
Invoke-InstallationCase 'client-options-exact-native-contract' {
    $options = @(Get-NetworkClientOptions)
    Assert-InstallationEqual 12 $options.Count 'Only the twelve reviewed native options are emitted'
    foreach ($option in $options) {
        Assert-InstallationEqual $true ($option -is [string]) 'Each output is one option string, not another object or nested collection'
        Assert-InstallationEqual $true ([regex]::IsMatch($option, '\A[A-Za-z][A-Za-z_]*:(?:true|false|[0-9]+(?:\.[0-9]+)?)\z')) 'Each option is a single exact native key/value line'
        $parts = $option.Split([char]':')
        Assert-InstallationEqual $true ($parts[0] -cin @($expectedClientOptions.Keys)) 'Unknown or case-mismatched native keys are not admitted'
        Assert-InstallationEqual $expectedClientOptions[$parts[0]] $parts[1] 'Each native option retains the reviewed QA value'
    }
}
Invoke-InstallationCase 'client-options-keys-are-unique' {
    $keys = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    foreach ($option in @(Get-NetworkClientOptions)) {
        Assert-InstallationEqual $true $keys.Add($option.Split([char]':')[0]) 'A repeated or case-aliased key cannot shadow another option'
    }
    Assert-InstallationEqual 12 $keys.Count 'All twelve distinct native keys are present'
    foreach ($key in $expectedClientOptions.Keys) {
        Assert-InstallationEqual $true $keys.Contains($key) 'Every required native option key is present'
    }
}
Invoke-InstallationCase 'client-options-native-simulation-and-frame-bounds' {
    $values = @{}
    foreach ($option in @(Get-NetworkClientOptions)) {
        $parts = $option.Split([char]':')
        $values[$parts[0]] = $parts[1]
    }
    $render = [int]$values.renderDistance
    $simulation = [int]$values.simulationDistance
    $fps = [int]$values.maxFps
    # The smaller native maximum is 16; these minima work in both hardware branches.
    Assert-InstallationEqual $true ($render -ge 2 -and $render -le 16) 'Render distance is in the pinned native range'
    Assert-InstallationEqual $true ($simulation -ge 5 -and $simulation -le 16) 'Simulation distance is in the pinned native range, not rejected 2'
    Assert-InstallationEqual 5 $simulation 'Use the real minimum instead of an invalid value that falls back to 12'
    Assert-InstallationEqual $true ($fps -ge 10 -and $fps -le 260 -and ($fps % 10) -eq 0) 'Frame cap follows the native integer-range times-ten codec'
    foreach ($key in @('onboardAccessibility', 'skipMultiplayerWarning', 'joinedFirstServer', 'pauseOnLostFocus', 'enableVsync', 'fullscreen')) {
        Assert-InstallationEqual $true ($values[$key] -cin @('true', 'false')) 'Native booleans use exact lowercase literals'
    }
    Assert-InstallationEqual '0' $values.graphicsMode 'FAST has the reviewed native graphics enum ID'
    Assert-InstallationEqual '2' $values.particles 'MINIMAL has the reviewed native particle enum ID'
    Assert-InstallationEqual '0.0' $values.soundCategory_master 'Muted volume uses a native invariant-decimal literal'
}
Invoke-InstallationCase 'client-options-contain-no-identity-token-or-path' {
    foreach ($option in @(Get-NetworkClientOptions)) {
        Assert-InstallationEqual $false ([regex]::IsMatch($option,
            '(?i)(?:username|uuid|accessToken|clientId|xuid|session|auth|token|password|account|gameDir|assetsDir|user\.home|gradle|[\\/]|[\r\n])')) 'Native options cannot contain launcher identity, credentials, paths or extra lines'
    }
}
Invoke-InstallationCase 'client-options-fresh-values-on-each-call' {
    $first = @(Get-NetworkClientOptions)
    $second = @(Get-NetworkClientOptions)
    Assert-InstallationEqual ([string]::Join("`n", $first)) ([string]::Join("`n", $second)) 'Repeated calls have deterministic option text'
    $first[0] = 'renderDistance:99'
    $third = @(Get-NetworkClientOptions)
    Assert-InstallationEqual ([string]::Join("`n", $second)) ([string]::Join("`n", $third)) 'Mutating a caller collection cannot change later options'
    Assert-InstallationEqual 12 $third.Count 'Fresh output still contains exactly the reviewed option set'
}

foreach ($hasClients in @($false, $true)) {
    $identityMode = if ($hasClients) { 'two-client' } else { 'server-only' }
    $expectedIdentityEvidence = [ordered]@{
        realLauncherAccountsRead = $false
        realAuthenticationCredentialsProvided = $false
        safeArgumentFileIdentityExcluded = $true
        capturedConsoleLauncherArgumentLinesRedacted = $true
        nativeDebugMayPersistSyntheticIdentity = $hasClients
    }
    Invoke-InstallationCase ('identity-evidence-exact-boolean-contract-' + $identityMode) {
        $evidence = Get-NetworkRuntimeIdentityEvidence -HasClients $hasClients
        Assert-InstallationEqual $true ($evidence -is [System.Collections.IDictionary]) 'Receipt identity evidence exposes a dictionary with Keys and indexed values'
        Assert-InstallationEqual 5 @($evidence.Keys).Count 'Only the five precise identity evidence fields are emitted'
        foreach ($key in $evidence.Keys) {
            Assert-InstallationEqual $true ($key -cin @($expectedIdentityEvidence.Keys)) 'Every identity field name is exact and recognized'
            Assert-InstallationEqual $true ($evidence[$key] -is [bool]) 'Identity evidence contains booleans, never identities, paths or credentials'
            Assert-InstallationEqual $expectedIdentityEvidence[$key] $evidence[$key] 'Identity evidence distinguishes capture policy from possible native debug persistence'
        }
        Assert-InstallationEqual $false (@($evidence.Keys) -contains 'identityArgumentsPersisted') 'The overly broad historical identity claim is not emitted'
    }
    Invoke-InstallationCase ('identity-evidence-fresh-deterministic-snapshot-' + $identityMode) {
        $first = Get-NetworkRuntimeIdentityEvidence -HasClients $hasClients
        $second = Get-NetworkRuntimeIdentityEvidence -HasClients $hasClients
        Assert-InstallationEqual $false ([object]::ReferenceEquals($first, $second)) 'Calls return independent caller-owned snapshots'
        foreach ($key in $expectedIdentityEvidence.Keys) {
            Assert-InstallationEqual $first[$key] $second[$key] 'Repeated calls have deterministic evidence values'
            $first[$key] = -not $first[$key]
        }
        $third = Get-NetworkRuntimeIdentityEvidence -HasClients $hasClients
        Assert-InstallationEqual $false ([object]::ReferenceEquals($first, $third)) 'A mutated snapshot is not reused later'
        foreach ($key in $expectedIdentityEvidence.Keys) {
            Assert-InstallationEqual $expectedIdentityEvidence[$key] $second[$key] 'Mutating one result cannot change another caller snapshot'
            Assert-InstallationEqual $expectedIdentityEvidence[$key] $third[$key] 'Mutating a result cannot change later evidence'
        }
    }
}
Invoke-InstallationCase 'identity-evidence-only-native-debug-possibility-varies-with-clients' {
    $server = Get-NetworkRuntimeIdentityEvidence -HasClients $false
    $clients = Get-NetworkRuntimeIdentityEvidence -HasClients $true
    foreach ($key in @('realLauncherAccountsRead', 'realAuthenticationCredentialsProvided',
        'safeArgumentFileIdentityExcluded', 'capturedConsoleLauncherArgumentLinesRedacted')) {
        Assert-InstallationEqual $server[$key] $clients[$key] 'Both QA modes retain the same real-account and capture policy'
    }
    Assert-InstallationEqual $false $server.nativeDebugMayPersistSyntheticIdentity 'Server-only mode does not launch synthetic client identities'
    Assert-InstallationEqual $true $clients.nativeDebugMayPersistSyntheticIdentity 'Client mode admits native debug persistence instead of claiming all identity is absent'
}

Assert-InstallationEqual 81 $script:InstallationCaseCount 'Every declared installer-inventory/native-option/identity-metadata case executed'
Write-Output ('NETWORK_RUNTIME_INSTALLATION_TESTS_PASSED cases=' + $script:InstallationCaseCount + ' assertions=' + $script:InstallationAssertionCount)

# QA only: FML-discovered libraries are not necessarily in version.json's bootstrap classpath.
# The official pinned install_profile.json is read as bounded data, never executed/downloaded.
function Get-NetworkClientOptions {
    # Pinned Options.java: renderDistance IntRange starts at 2, simulationDistance at 5.
    # A server's simulation-distance=2 is a different setting and remains unchanged.
    @('onboardAccessibility:false','skipMultiplayerWarning:true','joinedFirstServer:true',
      'pauseOnLostFocus:false','enableVsync:false','fullscreen:false',
      'renderDistance:2','simulationDistance:5','maxFps:20','graphicsMode:0',
      'particles:2','soundCategory_master:0.0')
}

function Get-NetworkRuntimeIdentityEvidence([bool]$HasClients) {
    # Describes the controlled QA launch/capture policy, without inspecting any
    # arguments, logs, account files or credentials. Native debug output is not
    # the redacted console capture: it can retain the clients' synthetic identity.
    # Each caller owns a fresh snapshot; this is not a claim that all game logs
    # are identity-free or evidence of a supported launcher restart mechanism.
    return [ordered]@{
        realLauncherAccountsRead = $false
        realAuthenticationCredentialsProvided = $false
        safeArgumentFileIdentityExcluded = $true
        capturedConsoleLauncherArgumentLinesRedacted = $true
        nativeDebugMayPersistSyntheticIdentity = $HasClients
    }
}

function Get-NetworkDiscoveryLibraries($Profile) {
    if ($Profile.spec -ne 1 -or $Profile.minecraft -cne '1.20.1' -or
        $Profile.version -cne '1.20.1-forge-47.4.23') { throw 'Unexpected Forge installation baseline.' }
    foreach ($name in @('fmlcore', 'javafmllanguage', 'lowcodelanguage', 'mclanguage', 'forge')) {
        $coordinate = 'net.minecraftforge:' + $name + ':1.20.1-47.4.23'
        $suffix = ''
        if ($name -ceq 'forge') { $coordinate += ':universal'; $suffix = '-universal' }
        $found = @($Profile.libraries | Where-Object { $_.name -ceq $coordinate })
        if ($found.Count -ne 1) { throw ('Missing or duplicate pinned discovery library: ' + $coordinate) }
        $artifact = $found[0].downloads.artifact
        $expected = 'net/minecraftforge/' + $name + '/1.20.1-47.4.23/' + $name + '-1.20.1-47.4.23' + $suffix + '.jar'
        if ($artifact.path -cne $expected -or $artifact.sha1 -cnotmatch '^[0-9a-f]{40}$' -or
            $artifact.size -isnot [ValueType] -or $artifact.size -is [bool] -or
            $artifact.size -le 0 -or $artifact.size -gt 16777216 -or
            [math]::Truncate([double]$artifact.size) -ne $artifact.size) {
            throw ('Invalid bounded discovery artifact metadata: ' + $coordinate)
        }
        [pscustomobject]@{name=$coordinate;path=$expected;sha1=$artifact.sha1;size=[long]$artifact.size}
    }
}

function Read-NetworkInstallerLibraries([string]$Path) {
    $entry = Get-Item -LiteralPath $Path -Force -ErrorAction Stop
    if ($entry.PSIsContainer) { throw 'Expected the pinned local Forge installer file.' }
    while ($null -ne $entry) {
        if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Installer aliases are not allowed.' }
        $entry = if ($entry -is [IO.DirectoryInfo]) { $entry.Parent } else { $entry.Directory }
    }
    if ((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne
        '75cfcb11f60cc641dc83757c1357c08c9bdba255ec529feb115c5e89a59cf75a') {
        throw 'Provide the pinned local Forge installer; no implicit download or execution is permitted.'
    }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $profiles = @($archive.Entries | Where-Object { $_.FullName -ceq 'install_profile.json' })
        if ($profiles.Count -ne 1 -or $profiles[0].Length -gt 1048576) { throw 'Invalid bounded installer profile.' }
        $reader = [IO.StreamReader]::new($profiles[0].Open())
        try { $profile = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        Get-NetworkDiscoveryLibraries $profile
    } finally { $archive.Dispose() }
}

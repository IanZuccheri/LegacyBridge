[CmdletBinding()]
param(
    [ValidateSet('p3-production', 'p4-ui', 'p5-transactions', 'p6-public', 'embedded-simple')][string]$ValidationRun = 'p3-production',
    [string]$JavaHome = 'C:\Program Files\Java\jdk-17',
    [string[]]$JvmArgument = @(),
    [switch]$Launch
)
. (Join-Path $PSScriptRoot 'ProductionClient.Common.ps1')
$clientDirectory = Assert-ProductionRoot $productionRoot
$java = Join-Path $JavaHome 'bin/java.exe'
$vanilla = Get-Content -LiteralPath (Join-Path $clientDirectory 'versions/1.20.1/1.20.1.json') -Raw | ConvertFrom-Json
$forge = Get-Content -LiteralPath (Join-Path $clientDirectory "versions/$productionVersionId/$productionVersionId.json") -Raw | ConvertFrom-Json
$receipt = Get-Content -LiteralPath (Join-Path $clientDirectory 'preparation.json') -Raw | ConvertFrom-Json
if ($receipt.schemaVersion -ne 2 -or @($receipt.processorOutputs).Count -ne 3) {
    throw 'Rerun Prepare-ProductionClient.ps1 to verify the complete installer-produced runtime before launching.'
}
foreach ($output in $receipt.processorOutputs) {
    $runtimeFile = Get-ProductionChild $clientDirectory $output.path
    if ($output.sha256 -notmatch '^[0-9a-f]{64}$' -or -not (Test-Path -LiteralPath $runtimeFile -PathType Leaf) -or
            (Get-FileHash -LiteralPath $runtimeFile -Algorithm SHA256).Hash -ne $output.sha256) {
        throw "Changed or missing prepared Forge processor output: $($output.path)"
    }
}
Assert-ProductionHash (Join-Path $clientDirectory 'versions/1.20.1/1.20.1.json') $receipt.vanillaMetadataSha1
if ($forge.mainClass -ne 'cpw.mods.bootstraplauncher.BootstrapLauncher' -or $forge.inheritsFrom -ne '1.20.1' -or
        $forge.id -ne $productionVersionId) { throw 'Not the pinned ordinary Forge client profile.' }
$classpath = [Collections.Generic.List[string]]::new()
foreach ($library in (Get-ProductionLibraries $vanilla $forge)) {
    $path = Get-ProductionChild (Join-Path $clientDirectory 'libraries') $library.downloads.artifact.path
    Assert-ProductionHash $path $library.downloads.artifact.sha1
    $classpath.Add($path)
}
$vanillaJar = Join-Path $clientDirectory 'versions/1.20.1/1.20.1.jar'
Assert-ProductionHash $vanillaJar $vanilla.downloads.client.sha1
# FML's normal CommonClientLaunchHandler supplies installer-produced client:srg/extra
# and ForgeClientLaunchHandler supplies forge:client/universal. Do not expose the
# inherited obfuscated vanilla JAR as an extra bootstrap library/module.
$variables = @{
    auth_player_name = 'OfflineProbe'
    version_name = $productionVersionId
    game_directory = $clientDirectory
    assets_root = (Join-Path $clientDirectory 'assets')
    assets_index_name = [string]$vanilla.assetIndex.id
    auth_uuid = '00000000000000000000000000000000'
    auth_access_token = '0'
    clientid = 'offline-probe'
    auth_xuid = '0'
    user_type = 'legacy'
    version_type = 'release'
    resolution_width = '1280'
    resolution_height = '720'
    natives_directory = (Join-Path $clientDirectory 'natives')
    launcher_name = 'LegacyBridgeProductionValidation'
    launcher_version = '1'
    classpath = ($classpath -join ';')
    library_directory = (Join-Path $clientDirectory 'libraries')
    classpath_separator = ';'
}
$arguments = @('-Xms512m', '-Xmx3g', '-Dfile.encoding=UTF-8', '-Djavax.net.ssl.trustStore=NONE',
    '-Djavax.net.ssl.trustStoreType=Windows-ROOT', ('-Djava.io.tmpdir=' + (Join-Path $clientDirectory 'tmp')),
    ('-Duser.home=' + (Join-Path $clientDirectory 'empty-user-home')))
$arguments += @(Expand-ProductionArguments $vanilla.arguments.jvm $variables)
$arguments += @(Expand-ProductionArguments $forge.arguments.jvm $variables)
$arguments += $JvmArgument
$arguments += $forge.mainClass
$arguments += @(Expand-ProductionArguments $vanilla.arguments.game $variables)
$arguments += @(Expand-ProductionArguments $forge.arguments.game $variables)
if (($arguments -join ' ') -match 'forgeclientuserdev|forge_gradle|[\\/]\.gradle[\\/]') { throw 'Development classpath leaked into normal-client validation.' }
# Java's own argument-file grammar safely handles Windows paths with spaces and long classpaths.
$argumentLines = @($arguments | ForEach-Object { '"' + $_.Replace('\', '\\').Replace('"', '\"') + '"' })
$argumentFile = Join-Path $clientDirectory 'launch-arguments.txt'
[IO.File]::WriteAllLines($argumentFile, $argumentLines, [Text.UTF8Encoding]::new($false))
Write-Output "Prepared ordinary Forge arguments (no account credentials): $argumentFile"
if ($Launch) {
    $previousJavaOptions = $env:JAVA_TOOL_OPTIONS
    $previousJdkOptions = $env:JDK_JAVA_OPTIONS
    $previousUnderscoreOptions = $env:_JAVA_OPTIONS
    try {
        $env:JAVA_TOOL_OPTIONS = $null
        $env:JDK_JAVA_OPTIONS = $null
        $env:_JAVA_OPTIONS = $null
        $process = Start-Process -FilePath $java -WorkingDirectory $clientDirectory -ArgumentList ('@"' + $argumentFile + '"') `
            -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $clientDirectory 'client-stdout.log') `
            -RedirectStandardError (Join-Path $clientDirectory 'client-stderr.log')
        Write-Output "Started owned ordinary-client PID: $($process.Id)"
    } finally {
        $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
        $env:JDK_JAVA_OPTIONS = $previousJdkOptions
        $env:_JAVA_OPTIONS = $previousUnderscoreOptions
    }
} else {
    Write-Output 'No game launched. Review prepared arguments, then invoke explicitly with -Launch.'
}

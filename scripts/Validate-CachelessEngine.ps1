[CmdletBinding()]
param(
    [string]$BundleDirectory = '',
    [switch]$BuiltinSimpleContent,
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [string]$FixtureJar = '',
    [string]$EvidenceDirectory = '',
    [ValidateSet('simple-content-v1','logic-preserving-v1','lifecycle-preserving-v1','food-item-v1','datapack-recipes-v1','network-snapshot-v1')][string]$ConversionProfile = 'simple-content-v1'
)

# Test harness only. Never edits a launcher profile, cache, original JAR or game installation.
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$javaExecutable = Join-Path $JavaHome 'bin/java.exe'
if (-not (Test-Path -LiteralPath $javaExecutable -PathType Leaf)) { throw 'Select an existing Java 17 home.' }
if ($BuiltinSimpleContent.IsPresent -eq (-not [string]::IsNullOrWhiteSpace($BundleDirectory))) {
    throw 'Select exactly one of -BuiltinSimpleContent or -BundleDirectory.'
}
if ($BuiltinSimpleContent -and $ConversionProfile -ne 'simple-content-v1') { throw 'Included data is simple-content-v1 only.' }
$bundlePath = if ($BuiltinSimpleContent) { $null } else { (Resolve-Path -LiteralPath $BundleDirectory).Path }
$distribution = Join-Path $projectDirectory 'legacy-converter/build/install/legacy-converter/lib'
$probeClasses = Join-Path $projectDirectory 'legacy-converter/build/classes/java/test/dev/legacybridge/converter/cli'
if (-not $FixtureJar) {
    $FixtureJar = Join-Path $projectDirectory 'fixtures/simple-content-1.16.5/build/libs/legacybridge-example-1.16.5-1.0.0.jar'
}
$fixturePath = (Resolve-Path -LiteralPath $FixtureJar).Path
if (-not (Test-Path -LiteralPath $distribution -PathType Container)) { throw 'Run :legacy-converter:installDist first.' }
$probeFiles = @(Get-ChildItem -LiteralPath $probeClasses -File | Where-Object { $_.Name -like 'CachelessEngineProbe*.class' })
if ($probeFiles.Count -eq 0) { throw 'Run :legacy-converter:testClasses first.' }

$isolatedDirectory = Join-Path ([IO.Path]::GetTempPath()) ('legacybridge-p2-clean-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $isolatedDirectory -ErrorAction Stop | Out-Null
foreach ($relative in @('lib', 'probe/dev/legacybridge/converter/cli', 'input', 'cli', 'service', 'reports', 'user', 'gradle', 'tmp')) {
    New-Item -ItemType Directory -Path (Join-Path $isolatedDirectory $relative) | Out-Null
}
foreach ($library in Get-ChildItem -LiteralPath $distribution -File) {
    Copy-Item -LiteralPath $library.FullName -Destination (Join-Path $isolatedDirectory 'lib') -ErrorAction Stop
}
foreach ($probe in $probeFiles) {
    Copy-Item -LiteralPath $probe.FullName -Destination (Join-Path $isolatedDirectory 'probe/dev/legacybridge/converter/cli') -ErrorAction Stop
}
if (-not $BuiltinSimpleContent) { Copy-Item -LiteralPath $bundlePath -Destination (Join-Path $isolatedDirectory 'bundle') -Recurse -ErrorAction Stop }
$fixtureName = [IO.Path]::GetFileName($fixturePath)
Copy-Item -LiteralPath $fixturePath -Destination (Join-Path $isolatedDirectory ('input/' + $fixtureName)) -ErrorAction Stop
$originalHash = (Get-FileHash -LiteralPath $fixturePath -Algorithm SHA256).Hash

$previousGradle = $env:GRADLE_USER_HOME
$previousBundle = $env:LEGACYBRIDGE_MAPPING_BUNDLE
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
$previousJdkOptions = $env:JDK_JAVA_OPTIONS
$previousUnderscoreOptions = $env:_JAVA_OPTIONS
try {
    $env:GRADLE_USER_HOME = Join-Path $isolatedDirectory 'gradle'
    $env:LEGACYBRIDGE_MAPPING_BUNDLE = $null
    $env:JAVA_TOOL_OPTIONS = $null
    $env:JDK_JAVA_OPTIONS = $null
    $env:_JAVA_OPTIONS = $null
    Push-Location -LiteralPath $isolatedDirectory
    try {
        $probeArguments = @($isolatedDirectory, $ConversionProfile, $fixtureName)
        if ($BuiltinSimpleContent) { $probeArguments += '--builtin-simple-content' }
        & $javaExecutable '-Xmx512m' '-Djava.security.manager=allow' `
            "-Duser.home=$(Join-Path $isolatedDirectory 'user')" `
            "-Djava.io.tmpdir=$(Join-Path $isolatedDirectory 'tmp')" `
            '-cp' 'probe;lib/*' 'dev.legacybridge.converter.cli.CachelessEngineProbe' @probeArguments
        $probeExit = $LASTEXITCODE
    } finally { Pop-Location }
    if ($probeExit -ne 0) { throw "Clean-cache probe failed ($probeExit); owned diagnostic directory retained: $isolatedDirectory" }
} finally {
    $env:GRADLE_USER_HOME = $previousGradle
    $env:LEGACYBRIDGE_MAPPING_BUNDLE = $previousBundle
    $env:JAVA_TOOL_OPTIONS = $previousJavaOptions
    $env:JDK_JAVA_OPTIONS = $previousJdkOptions
    $env:_JAVA_OPTIONS = $previousUnderscoreOptions
}
if ((Get-FileHash -LiteralPath $fixturePath -Algorithm SHA256).Hash -ne $originalHash) { throw 'Original fixture changed.' }
if ($EvidenceDirectory) {
    # Fresh destination only; reports and local-use data are never a public release asset.
    if (Test-Path -LiteralPath $EvidenceDirectory) { throw 'EvidenceDirectory must not already exist.' }
    Copy-Item -LiteralPath (Join-Path $isolatedDirectory 'reports') -Destination $EvidenceDirectory -Recurse -ErrorAction Stop
}
Write-Output "Isolated installation retained for inspection: $isolatedDirectory"

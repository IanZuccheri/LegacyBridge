[CmdletBinding()]
param([Parameter(Mandatory = $true)][string]$JavaHome)

# Local QA only: no Gradle, game launch, downloads, client input changes or packaging.
$ErrorActionPreference = 'Stop'
$projectDirectory = (Resolve-Path -LiteralPath (Split-Path -Parent $PSScriptRoot)).Path
$javaExecutable = Join-Path $JavaHome 'bin/java.exe'
$compilerExecutable = Join-Path $JavaHome 'bin/javac.exe'
foreach ($executable in @($javaExecutable, $compilerExecutable)) {
    if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) { throw 'Select an existing Java 17 JDK.' }
}
$distribution = Join-Path $projectDirectory 'legacy-converter/build/install/legacy-converter/lib'
if (-not (Test-Path -LiteralPath $distribution -PathType Container)) {
    throw 'The existing CLI development distribution is required; this script does not build it.'
}
$fixtureDirectory = Join-Path $projectDirectory 'build/p6-public/negative-fixtures'
$classesDirectory = Join-Path $fixtureDirectory 'tools/classes'
New-Item -ItemType Directory -Path $classesDirectory -Force | Out-Null
$source = Join-Path $PSScriptRoot 'P6NegativeFixtures.java'
& $compilerExecutable '--release' '17' '-encoding' 'UTF-8' '-cp' "$distribution/*" '-d' $classesDirectory $source
if ($LASTEXITCODE -ne 0) { throw 'P6 fixture helper compilation failed.' }
& $javaExecutable '-Xmx512m' '-cp' "$classesDirectory;$distribution/*" 'P6NegativeFixtures' $projectDirectory
if ($LASTEXITCODE -ne 0) { throw 'P6 qualification failed. Owned evidence has been retained; no originals were replaced.' }

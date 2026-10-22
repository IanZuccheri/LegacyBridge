# Pure mode/hash/world-prefix contract tests. No server preparation, game processes or disk writes.
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Test-P6PublicServer.ps1'), [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count -ne 0) { throw 'Dedicated-server runner contains PowerShell parse errors.' }
# Import only the explicit pure selector; dot-sourcing the full runner would perform filesystem preflight.
$selectors = @($ast.FindAll({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Get-P6ValidationProfile' }, $false))
if ($selectors.Count -ne 1) { throw 'Expected exactly one pure validation selector.' }
. ([scriptblock]::Create($selectors[0].Extent.Text))
$script:cases = 0
function Check([string]$Name, [scriptblock]$Action) {
    try { $null = & $Action } catch { throw ('Preflight contract failed: ' + $Name + '; ' + $_.Exception.Message) }
    $script:cases++
}
function Equal([object]$Expected, [object]$Actual) { if ($Expected -cne $Actual) { throw ('Expected ' + $Expected + ', received ' + $Actual) } }
function Reject([scriptblock]$Action) {
    $failed = $false
    try { $null = & $Action } catch { $failed = $true }
    if (-not $failed) { throw 'Expected explicit refusal.' }
}
$public = 'a' * 64
$converted = 'b' * 64

Check 'historical-pins-and-evidence-unchanged' {
    $profile = Get-P6ValidationProfile 'p6-public' '' ''
    Equal '00cc7ebde1279c1ac3cc6ac0a54498e4598ca4f45700a6799f915ce9dddea991' $profile.PublicSha256
    Equal '541cc64b44b97d0e4e79a193dd2ce3a86a5feba6e15714d0ee24ad63751492bb' $profile.ConvertedSha256
    Equal 'p6-public-server' $profile.EvidencePrefix
    Equal 'P6_PUBLIC_SERVER' $profile.OutputPrefix
    Equal 'P6_PUBLIC_ONLY_DEDICATED_SERVER' $profile.Scenario
    Equal $true ('p6-fresh1' -cmatch $profile.LevelNamePattern)
    Equal $false ('embedded-simple-fresh1' -cmatch $profile.LevelNamePattern)
}
Check 'historical-public-pin-cannot-change' { Reject { Get-P6ValidationProfile 'p6-public' $public '' } }
Check 'historical-converted-pin-cannot-change' { Reject { Get-P6ValidationProfile 'p6-public' '' $converted } }
Check 'embedded-explicit-identities-and-separated-evidence' {
    $profile = Get-P6ValidationProfile 'embedded-simple' $public $converted
    Equal $public $profile.PublicSha256
    Equal $converted $profile.ConvertedSha256
    Equal 'embedded-simple-server' $profile.EvidencePrefix
    Equal 'EMBEDDED_SIMPLE_SERVER' $profile.OutputPrefix
    Equal 'EMBEDDED_SIMPLE_PUBLIC_ONLY_DEDICATED_SERVER' $profile.Scenario
    Equal $true ('embedded-simple-fresh1' -cmatch $profile.LevelNamePattern)
    Equal $false ('p6-fresh1' -cmatch $profile.LevelNamePattern)
    Equal $false ('embedded-simple-../existing' -cmatch $profile.LevelNamePattern)
    Equal $false ('embedded-simple-existing/world' -cmatch $profile.LevelNamePattern)
    Equal $false ('embedded-simple-existing\world' -cmatch $profile.LevelNamePattern)
    Equal $false ('embedded-simple-' -cmatch $profile.LevelNamePattern)
    Equal $false (('embedded-simple-' + ('a' * 41)) -cmatch $profile.LevelNamePattern)
}
Check 'embedded-missing-both-hashes' { Reject { Get-P6ValidationProfile 'embedded-simple' '' '' } }
Check 'embedded-missing-public-hash' { Reject { Get-P6ValidationProfile 'embedded-simple' '' $converted } }
Check 'embedded-missing-converted-hash' { Reject { Get-P6ValidationProfile 'embedded-simple' $public '' } }
foreach ($bad in @('a', ('a' * 63), ('a' * 65), ('A' * 64), ('g' * 64), (' ' + $public), ($public + "`n"))) {
    Check ('embedded-reject-invalid-hash-' + $script:cases) {
        Reject { Get-P6ValidationProfile 'embedded-simple' $bad $converted }
        Reject { Get-P6ValidationProfile 'embedded-simple' $public $bad }
    }
}
Check 'unknown-mode-rejected' { Reject { Get-P6ValidationProfile 'unknown' $public $converted } }
Check 'traversal-mode-rejected' { Reject { Get-P6ValidationProfile '../p6-public' $public $converted } }
Check 'runner-defaults-preserved' {
    $parameter = @($ast.ParamBlock.Parameters | Where-Object { $_.Name.VariablePath.UserPath -ceq 'ValidationRun' })
    Equal 1 $parameter.Count
    Equal 'p6-public' $parameter[0].DefaultValue.Value
}
Equal 17 $script:cases
[pscustomobject]@{ status = 'PASSED'; reportType = 'PUBLIC_SERVER_PREFLIGHT_CONTRACT'; cases = $script:cases;
    gamesLaunched = $false; filesystemPreflightRun = $false; filesWritten = $false } | ConvertTo-Json -Compress

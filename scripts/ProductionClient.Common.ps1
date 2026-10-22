# Validation helpers, never included in the public mod. No launcher-account/profile reads.
$ErrorActionPreference = 'Stop'
$productionProject = Split-Path -Parent $PSScriptRoot
if (-not (Get-Variable -Name ValidationRun -ErrorAction SilentlyContinue)) { $ValidationRun = 'p3-production' }
if ($ValidationRun -notin @('p3-production', 'p4-ui', 'p5-transactions', 'p6-public', 'embedded-simple')) { throw 'Unknown owned validation run.' }
$productionRoot = [IO.Path]::GetFullPath((Join-Path $productionProject "build/$ValidationRun/client"))
$productionVersionId = '1.20.1-forge-47.4.23'

function Assert-ProductionRoot([string]$Directory) {
    $resolved = [IO.Path]::GetFullPath($Directory).TrimEnd('\', '/')
    if ($resolved -ne $productionRoot) { throw "This validation script only owns build/$ValidationRun/client." }
    if (Test-Path -LiteralPath $resolved) {
        if ((Get-Item -LiteralPath $resolved).Attributes -band [IO.FileAttributes]::ReparsePoint) {
            throw 'The disposable client directory must not be an alias/reparse point.'
        }
    }
    return $resolved
}

function Get-ProductionChild([string]$Parent, [string]$Relative) {
    if ([IO.Path]::IsPathRooted($Relative) -or ($Relative -split '[/\\]' | Where-Object { $_ -eq '..' })) {
        throw "Unsafe manifest-relative path: $Relative"
    }
    $child = [IO.Path]::GetFullPath((Join-Path $Parent $Relative))
    if (-not $child.StartsWith($productionRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Manifest path escaped the disposable installation.'
    }
    return $child
}

function Test-ProductionRules($Item) {
    if (-not $Item.rules) { return $true }
    $allowed = $false
    foreach ($rule in $Item.rules) {
        $match = $true
        if ($rule.os.name -and $rule.os.name -ne 'windows') { $match = $false }
        if ($rule.os.arch -and 'amd64' -notmatch $rule.os.arch) { $match = $false }
        if ($rule.os.version -and [Environment]::OSVersion.Version.ToString() -notmatch $rule.os.version) { $match = $false }
        # Validation is offline, not demo/quickplay; only explicit fixed window size is enabled.
        if ($rule.features) {
            foreach ($feature in $rule.features.PSObject.Properties) {
                $actual = $feature.Name -eq 'has_custom_resolution'
                if ([bool]$feature.Value -ne $actual) { $match = $false }
            }
        }
        if ($match) { $allowed = $rule.action -eq 'allow' }
    }
    return $allowed
}

function Get-ProductionLibraries($Vanilla, $Forge) {
    $selected = [ordered]@{}
    foreach ($library in @($Vanilla.libraries) + @($Forge.libraries)) {
        if (-not (Test-ProductionRules $library)) { continue }
        $coordinate = $library.name -split ':'
        if ($coordinate.Count -lt 3) { throw 'Malformed official library coordinate.' }
        $key = $coordinate[0] + ':' + $coordinate[1]
        if ($coordinate.Count -gt 3) { $key += ':' + $coordinate[3] }
        $selected[$key] = $library
    }
    return @($selected.Values)
}

function Assert-ProductionHash([string]$File, [string]$Sha1) {
    if ($Sha1 -notmatch '^[0-9a-f]{40}$' -or -not (Test-Path -LiteralPath $File -PathType Leaf)) {
        throw "Missing artifact or expected official SHA1: $File"
    }
    if ((Get-FileHash -LiteralPath $File -Algorithm SHA1).Hash -ne $Sha1) { throw "Artifact checksum mismatch: $File" }
}

function Expand-ProductionArguments($Arguments, [hashtable]$Variables) {
    foreach ($argument in $Arguments) {
        if ($argument -is [string]) { $values = @($argument) }
        elseif (Test-ProductionRules $argument) { $values = @($argument.value) }
        else { continue }
        foreach ($value in $values) {
            $expanded = [string]$value
            foreach ($name in $Variables.Keys) { $expanded = $expanded.Replace('${' + $name + '}', [string]$Variables[$name]) }
            if ($expanded -match '\$\{') { throw "Unresolved launcher argument: $expanded" }
            $expanded
        }
    }
}

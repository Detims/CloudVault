param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string] $Path
)

$resolvedPath = Resolve-Path -LiteralPath $Path -ErrorAction Stop
Get-Content -LiteralPath $resolvedPath | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith('#')) { return }

    $separator = $line.IndexOf('=')
    if ($separator -lt 1) { throw "Invalid environment entry in $resolvedPath" }

    $name = $line.Substring(0, $separator).Trim()
    $value = $line.Substring($separator + 1).Trim()
    if ($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {
        $value = $value.Substring(1, $value.Length - 2)
    }
    [Environment]::SetEnvironmentVariable($name, $value, 'Process')
}

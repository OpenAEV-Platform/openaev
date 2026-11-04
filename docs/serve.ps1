# Serves the docs at http://localhost:8000. run.py sets up docs\.venv with the pinned requirements first.
# Set $env:DOCS_GIT_PLUGINS = "false" to skip the GitHub API calls of the git plugins.
#
# Python: $env:PYTHON when set (it must work and be recent enough), otherwise the first of py -3,
# python3, python, python3.20 ... python3.<minimum> that is at least the version of .python-version.
$ErrorActionPreference = "Stop"
$minimum = [version]((Get-Content "$PSScriptRoot\.python-version" -Raw).Trim())

# The version an interpreter reports with --version, or $null when it does not run.
function Get-PythonVersion([string]$Command, [string[]]$Arguments) {
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) { return $null }
    try { $output = & $Command @Arguments --version 2>&1 | Out-String } catch { return $null }
    if ($LASTEXITCODE -ne 0 -or $output -notmatch 'Python (\d+)\.(\d+)') { return $null }
    return [version]"$($Matches[1]).$($Matches[2])"
}

if ($env:PYTHON) {
    $found = Get-PythonVersion $env:PYTHON @()
    if ($null -eq $found -or $found -lt $minimum) {
        Write-Error "PYTHON=$env:PYTHON is not a Python $minimum or later."
        exit 4
    }
    $command = $env:PYTHON
    $arguments = @()
} else {
    $candidates = @(@{ Command = "py"; Arguments = @("-3") }, @{ Command = "python3"; Arguments = @() },
        @{ Command = "python"; Arguments = @() })
    for ($n = 20; $n -ge $minimum.Minor; $n--) {
        $candidates += @{ Command = "python$($minimum.Major).$n"; Arguments = @() }
    }
    $command = $null
    foreach ($candidate in $candidates) {
        $found = Get-PythonVersion $candidate.Command $candidate.Arguments
        if ($null -ne $found -and $found -ge $minimum) {
            $command = $candidate.Command
            $arguments = $candidate.Arguments
            break
        }
    }
    if ($null -eq $command) {
        Write-Error "No Python $minimum or later found (py -3, python3, python, python3.N). Install one, or set PYTHON."
        exit 4
    }
}
Write-Host "Docs: using $command $($arguments -join ' ')"
& $command @arguments "$PSScriptRoot\run.py" serve @args
exit $LASTEXITCODE

# Serves the docs at http://localhost:8000. run.py sets up docs\.venv with the pinned requirements first.
# Set $env:DOCS_GIT_PLUGINS = "false" to skip the GitHub API calls of the git plugins.
#
# Python, the same rule as the backend build and `yarn check-docs-links`: $env:OPENAEV_DOCS_PYTHON
# when set, which must then be usable, otherwise the first usable of py -3, python3, python,
# python3.20 ... python3.<minimum>. Usable: it runs, has the venv and ensurepip modules, and is at
# least the version of .python-version.
$ErrorActionPreference = "Stop"
$minimum = [version]((Get-Content "$PSScriptRoot\.python-version" -Raw).Trim())
$probe = "import sys, venv, ensurepip; print('%d.%d' % sys.version_info[:2])"

# The version of a usable interpreter, or $null.
function Get-PythonVersion([string]$Command, [string[]]$Arguments) {
    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) { return $null }
    try { $output = & $Command @Arguments -c $probe 2>$null | Out-String } catch { return $null }
    if ($LASTEXITCODE -ne 0 -or $output -notmatch '^\s*(\d+)\.(\d+)\s*$') { return $null }
    $version = [version]"$($Matches[1]).$($Matches[2])"
    if ($version -lt $minimum) { return $null }
    return $version
}

# Write-Error stops the script under ErrorActionPreference Stop before any exit code: print instead.
function Stop-WithSetupError([string]$Message) {
    [Console]::Error.WriteLine($Message)
    exit 4
}

if ($env:OPENAEV_DOCS_PYTHON) {
    if ($null -eq (Get-PythonVersion $env:OPENAEV_DOCS_PYTHON @())) {
        Stop-WithSetupError "OPENAEV_DOCS_PYTHON=$env:OPENAEV_DOCS_PYTHON is not a Python $minimum or later with the venv module."
    }
    $command = $env:OPENAEV_DOCS_PYTHON
    $arguments = @()
} else {
    $candidates = @(@{ Command = "py"; Arguments = @("-3") }, @{ Command = "python3"; Arguments = @() },
        @{ Command = "python"; Arguments = @() })
    for ($n = 20; $n -ge $minimum.Minor; $n--) {
        $candidates += @{ Command = "python$($minimum.Major).$n"; Arguments = @() }
    }
    $command = $null
    foreach ($candidate in $candidates) {
        if ($null -ne (Get-PythonVersion $candidate.Command $candidate.Arguments)) {
            $command = $candidate.Command
            $arguments = $candidate.Arguments
            break
        }
    }
    if ($null -eq $command) {
        Stop-WithSetupError ("No Python $minimum or later with the venv module found (py -3, python3, python, " +
            "python3.N). Install one, or set OPENAEV_DOCS_PYTHON.")
    }
}
Write-Host "Docs: using $command $($arguments -join ' ')"
& $command @arguments "$PSScriptRoot\run.py" serve @args
exit $LASTEXITCODE

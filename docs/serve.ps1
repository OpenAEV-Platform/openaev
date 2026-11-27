# Serves the docs at http://localhost:8000 and rebuilds them on every change. Options go to
# `properdocs serve`. Set $env:DOCS_GIT_PLUGINS = "false" to skip the GitHub API calls of the git plugins.
#
# This script only starts a Python: $env:OPENAEV_DOCS_PYTHON when set, otherwise the first of
# py -3, python3 and python that answers --version, which skips the Microsoft Store stub. docs\run.py
# then checks it and, when it is too old or has no venv module, finds a newer one itself
# (docs\README.md, "Which Python runs the docs toolchain").
$ErrorActionPreference = "Stop"

# True when the command runs and prints its version within 10 s, without the variables that
# redirect the imports of Python.
function Test-Python([string]$Command, [string[]]$Arguments) {
    $found = Get-Command $Command -CommandType Application -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $found) { return $false }
    try {
        $info = New-Object System.Diagnostics.ProcessStartInfo
        $info.FileName = $found.Source
        $info.Arguments = (@($Arguments) + "--version") -join " "
        $info.UseShellExecute = $false
        $info.RedirectStandardOutput = $true
        $info.RedirectStandardError = $true
        foreach ($name in "PYTHONPATH", "PYTHONHOME", "PYTHONSAFEPATH") { $info.EnvironmentVariables.Remove($name) }
        $process = [System.Diagnostics.Process]::Start($info)
        # Read both streams before waiting, so a full pipe never blocks the child.
        $output = $process.StandardOutput.ReadToEndAsync()
        $errors = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(10000)) {
            $process.Kill()
            return $false
        }
        return $process.ExitCode -eq 0 -and "$($output.Result)$($errors.Result)" -match "Python 3\."
    } catch {
        return $false
    }
}

if ($env:OPENAEV_DOCS_PYTHON) {
    $command = $env:OPENAEV_DOCS_PYTHON
    if (($command -match "[\\/]") -and -not [System.IO.Path]::IsPathRooted($command)) {
        # Relative to the current folder, which run.py does not keep.
        $command = [System.IO.Path]::GetFullPath((Join-Path (Get-Location).Path $command))
        $env:OPENAEV_DOCS_PYTHON = $command
    }
    $arguments = @()
} else {
    $command = $null
    foreach ($candidate in @(@{ Command = "py"; Arguments = @("-3") }, @{ Command = "python3"; Arguments = @() },
            @{ Command = "python"; Arguments = @() })) {
        if (Test-Python $candidate.Command $candidate.Arguments) {
            $command = $candidate.Command
            $arguments = $candidate.Arguments
            break
        }
    }
    if ($null -eq $command) {
        # Write-Error stops the script under ErrorActionPreference Stop before any exit code: print instead.
        $minimum = (Get-Content "$PSScriptRoot\.python-version" -Raw).Trim()
        [Console]::Error.WriteLine("No Python found (py -3, python3, python). Install Python $minimum or later, " +
            "or set OPENAEV_DOCS_PYTHON.")
        exit 4
    }
}
# The variables that redirect the imports of Python would break run.py itself too.
foreach ($name in "PYTHONPATH", "PYTHONHOME", "PYTHONSAFEPATH") { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
& $command @arguments "$PSScriptRoot\run.py" serve @args
exit $LASTEXITCODE

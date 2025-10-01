param([string]$arg)

if(-not $arg){ $arg = "NOOP" }

# Clean up URL encoding before anything else
$argClean = $arg.Replace('%3d', '=').Replace('%2b', '+').Replace('%2f', '/')

$command = "-ExecutionPolicy Bypass -WindowStyle Hidden -NonInteractive -NoProfile -Command `"Invoke-Expression ([System.Text.Encoding]::UTF8.GetString([convert]::FromBase64String('$argClean')))`""

Write-Host "[DEBUG] Command: powershell.exe $command"

$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $command
$principal = New-ScheduledTaskPrincipal -UserID "SYSTEM" -LogonType ServiceAccount -RunLevel Highest
$taskName = "OpenAEV-Payload-" + [guid]::NewGuid().ToString()

try {
    Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -ErrorAction Stop
    Write-Host "[OK] Task $taskName registered"
} catch {
    Write-Host "[ERROR] Register-ScheduledTask : $($_.Exception.Message)"
}

try {
    Start-ScheduledTask -TaskName $taskName -ErrorAction Stop
    Write-Host "[OK] Task $taskName started"
} catch {
    Write-Host "[ERROR] Start-ScheduledTask : $($_.Exception.Message)"
}

# Wait until the task has actually run
$maxWait = 180  # maximum wait time in seconds
$elapsed = 0
$started = $false

while ($elapsed -lt $maxWait -and -not $started) {
    $info = Get-ScheduledTaskInfo -TaskName $taskName -ErrorAction SilentlyContinue
    if ($info -and $info.LastRunTime -ne $null) {
        Write-Host "[OK] Task $taskName has executed at $($info.LastRunTime)"
        $started = $true
    } else {
        Start-Sleep -Seconds 1
        $elapsed++
    }
}

if (-not $started) {
    Write-Host "[WARN] Task $taskName did not confirm execution within $maxWait seconds"
}

try {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction Stop
    Write-Host "[OK] Task $taskName removed"
} catch {
    Write-Host "[ERROR] Unregister-ScheduledTask : $($_.Exception.Message)"
}

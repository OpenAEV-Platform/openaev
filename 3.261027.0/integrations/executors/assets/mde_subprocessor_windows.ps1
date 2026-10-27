if ($args.Count -lt 1 -or [string]::IsNullOrEmpty($args[0])) {
    Write-Error "OpenAEV subprocessor: no argument received (expected Base64 command as first parameter)"
    exit 1
}
$decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($args[0]))
Invoke-Expression $decoded

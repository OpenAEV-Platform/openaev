# Post-edit hook: run Spotless format
param([string]$M="")
$ErrorActionPreference = 'Stop'
$ProjectDir = (Resolve-Path (Join-Path $PSScriptRoot '..\..'))
Push-Location $ProjectDir

# Extract required Java version from pom.xml
$javaVersion = ([xml](Get-Content 'pom.xml')).project.properties.'java.version'
if (-not $javaVersion) { Write-Error 'java.version not found in pom.xml'; Pop-Location; exit 1 }

# Resolve JAVA_HOME if not set or wrong version
$needsJdk = $true
if ($env:JAVA_HOME) {
    $verOutput = cmd /c "`"$env:JAVA_HOME\bin\java`" -version 2>&1"
    if ($verOutput -match $javaVersion) { $needsJdk = $false }
}
if ($needsJdk) {
    $jdk = Get-ChildItem "$env:USERPROFILE\.jdks" -Directory -ErrorAction SilentlyContinue | Where-Object { $_.Name -match $javaVersion } | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}

# Resolve mvn: PATH > Maven Wrapper local > .m2/wrapper
$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn -and (Test-Path '.\mvnw.cmd')) { $mvn = '.\mvnw.cmd' }
if (-not $mvn) {
    $mvn = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Filter 'mvn.cmd' -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mvn) { Write-Error 'mvn not found'; Pop-Location; exit 1 }

$p = if ($M) { @('-pl', $M) } else { @() }
& $mvn spotless:apply @p -q
if ($LASTEXITCODE -ne 0) { Write-Error 'spotless:apply failed'; Pop-Location; exit 1 }

# Verify
& $mvn spotless:check @p -q
if ($LASTEXITCODE -ne 0) { Write-Error 'spotless:check failed'; Pop-Location; exit 1 }

Write-Host 'OK'
Pop-Location

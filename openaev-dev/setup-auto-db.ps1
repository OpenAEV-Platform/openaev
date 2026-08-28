# ==============================================================================
# OpenAEV -- Auto-DB Installer (PowerShell)
# ==============================================================================
#
# Copies the DevDatabaseEnvironmentPostProcessor and its Spring registration
# from  openaev-dev/test-containers/  into  openaev-api/  so the backend can
# auto-start a PostgreSQL container on launch (dev profile only).
# Supports both Podman and Docker (auto-detected at runtime).
#
# The copied files are git-ignored -- they never pollute the API module in VCS.
#
# Usage:
#   cd openaev-dev; .\setup-auto-db.ps1             # from openaev-dev/
#   powershell -File openaev-dev\setup-auto-db.ps1   # from project root
#
# To uninstall:
#   Remove-Item openaev-api\src\main\java\io\openaev\config\DevDatabaseEnvironmentPostProcessor.java
#   Remove-Item openaev-api\src\main\resources\META-INF\spring.factories
# ==============================================================================

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RootDir    = (Resolve-Path "$ScriptDir\..").Path

$SourceDir  = Join-Path $ScriptDir "test-containers"
$JavaDest   = Join-Path $RootDir   "openaev-api\src\main\java\io\openaev\config"
$MetaDest   = Join-Path $RootDir   "openaev-api\src\main\resources\META-INF"

# --- Sanity checks -----------------------------------------------------------

if (-not (Test-Path $SourceDir -PathType Container)) {
    Write-Error "Source directory not found: $SourceDir"
    exit 1
}

foreach ($file in @("DevDatabaseEnvironmentPostProcessor.java", "spring.factories")) {
    if (-not (Test-Path (Join-Path $SourceDir $file))) {
        Write-Error "Missing source file: $SourceDir\$file"
        exit 1
    }
}

# --- Copy Java class ----------------------------------------------------------

if (-not (Test-Path $JavaDest)) { New-Item -ItemType Directory -Path $JavaDest -Force | Out-Null }
Copy-Item (Join-Path $SourceDir "DevDatabaseEnvironmentPostProcessor.java") -Destination $JavaDest -Force
Write-Host "  [OK] Copied DevDatabaseEnvironmentPostProcessor.java -> $JavaDest\"

# --- Copy / merge spring.factories -------------------------------------------

if (-not (Test-Path $MetaDest)) { New-Item -ItemType Directory -Path $MetaDest -Force | Out-Null }

$FactoriesFile = Join-Path $MetaDest "spring.factories"
$EppLine       = "io.openaev.config.DevDatabaseEnvironmentPostProcessor"
$EppKey        = "org.springframework.boot.env.EnvironmentPostProcessor"

if (Test-Path $FactoriesFile) {
    $content = Get-Content $FactoriesFile -Raw
    if ($content -match [regex]::Escape($EppLine)) {
        Write-Host "  [OK] spring.factories already contains the auto-db entry -- skipped."
    }
    elseif ($content -match "^$([regex]::Escape($EppKey))=") {
        # Key exists -- append with comma + backslash continuation
        $lines = Get-Content $FactoriesFile
        $newLines = @()
        foreach ($line in $lines) {
            if ($line -match "^$([regex]::Escape($EppKey))=") {
                $newLines += "$line,\"
            } else {
                $newLines += $line
            }
        }
        $newLines += "  $EppLine"
        $newLines | Set-Content $FactoriesFile -Encoding UTF8
        Write-Host "  [OK] Appended auto-db entry to existing spring.factories"
    }
    else {
        # Key doesn't exist -- add it
        Add-Content $FactoriesFile -Value ""
        Get-Content (Join-Path $SourceDir "spring.factories") | Add-Content $FactoriesFile
        Write-Host "  [OK] Added auto-db entry to spring.factories"
    }
}
else {
    Copy-Item (Join-Path $SourceDir "spring.factories") -Destination $FactoriesFile
    Write-Host "  [OK] Copied spring.factories -> $MetaDest\"
}

# --- Done ---------------------------------------------------------------------

Write-Host ""
Write-Host "================================================================"
Write-Host "  [OK] Auto-DB setup complete!"
Write-Host "================================================================"
Write-Host ""
Write-Host "  The backend will now auto-start a PostgreSQL container"
Write-Host "  (using Podman or Docker, auto-detected) when launched with"
Write-Host "  the 'dev' profile and the property:"
Write-Host ""
Write-Host "    openaev.dev.auto-start-database=true"
Write-Host ""
Write-Host "  Optional -- use a fixed port instead of a per-branch port:"
Write-Host ""
Write-Host "    openaev.dev.database-port=5432"
Write-Host ""
Write-Host "  Optional -- force a specific container runtime:"
Write-Host ""
Write-Host "    openaev.dev.container-runtime=podman"
Write-Host ""
Write-Host "  All properties go in application-dev.properties."
Write-Host ""
Write-Host "  These files are git-ignored and will NOT be committed."
Write-Host ""

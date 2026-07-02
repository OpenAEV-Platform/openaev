# Usage: .\restore-db.ps1 -DumpFile C:\path\to\backup.dump
param([Parameter(Mandatory=$true)][string]$DumpFile)

$Container = "openaev-dev-pgsql"
$DbUser    = "openaev"
$DbName    = "openaev"

if (-not (Test-Path $DumpFile)) { Write-Error "File not found: $DumpFile"; exit 1 }

$FileName      = Split-Path -Leaf $DumpFile
$ContainerPath = "/tmp/$FileName"

Write-Host "Copying dump into container..."
podman cp $DumpFile "${Container}:${ContainerPath}"

Write-Host "Resetting schema..."
podman exec $Container psql -U $DbUser -d $DbName -c "DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public; ALTER SCHEMA public OWNER TO $DbUser;"

Write-Host "Restoring..."
if ($FileName -match "\.sql$") {
    podman exec $Container bash -c "psql -U $DbUser -d $DbName < $ContainerPath"
} else {
    podman exec $Container pg_restore -U $DbUser -d $DbName -1 --no-owner --role=$DbUser $ContainerPath
}

Write-Host "Cleaning up..."
podman exec $Container rm -f $ContainerPath

Write-Host "Done!"

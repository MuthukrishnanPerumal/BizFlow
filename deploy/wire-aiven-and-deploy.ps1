# Wires the existing Aiven MySQL (defaultdb) to Render and redeploys the API.
# Run in PowerShell from the repo root. Secrets are prompted; nothing is committed.
#
#   cd "d:\MK\Aveline BizFlow"
#   .\deploy\wire-aiven-and-deploy.ps1

$ErrorActionPreference = "Stop"

$aivenHost = "mysql-1379bbe4-pmkrish1997-25f8.b.aivencloud.com"
$aivenPort = "13622"
$aivenDb   = "defaultdb"
$aivenUser = "avnadmin"
$serviceId = "srv-db7r4b59fdbs73f81cpg"
$frontend  = "https://frontend-zeta-topaz-48.vercel.app"

Write-Host "Aiven host: $aivenHost"
Write-Host "Database already exists ($aivenDb). Flyway will create tables on first successful API boot."
Write-Host ""

if (-not $env:RENDER_API_KEY) {
    $secureKey = Read-Host "Paste Render API key (from https://dashboard.render.com/u/settings#api-keys)" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
    try { $env:RENDER_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

$securePwd = Read-Host "Paste Aiven password (Reveal/Copy in Aiven console)" -AsSecureString
$bstr2 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePwd)
try { $dbPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr2) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr2) }
if ([string]::IsNullOrWhiteSpace($dbPassword)) {
    Write-Error "Aiven password is required."
}

# Generate a production JWT secret if not provided
if (-not $env:JWT_SECRET -or $env:JWT_SECRET.Length -lt 32) {
    $bytes = New-Object byte[] 48
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $env:JWT_SECRET = [Convert]::ToBase64String($bytes)
    Write-Host "Generated JWT_SECRET (stored only on Render)."
}

$env:DB_URL = "jdbc:mysql://${aivenHost}:${aivenPort}/${aivenDb}?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=UTF-8&connectionTimeZone=UTC"
$env:DB_USERNAME = $aivenUser
$env:DB_PASSWORD = $dbPassword
$env:RENDER_SERVICE_ID = $serviceId
$env:CORS_ALLOWED_ORIGINS = $frontend

Write-Host "Updating Render env and deploying..."
& "$PSScriptRoot\render-set-db-env.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Frontend already uses API_URL=https://aveline-bizflow-api.onrender.com"
Write-Host "Open app: $frontend"
Write-Host "Default admin (change password on first use): admin / Aveline#Admin1"

# =============================================================================
# Aveline BizFlow - Production Go-Live (one command)
# =============================================================================
# Prerequisites:
#   1. Aiven MySQL Free already created
#   2. Render API key: https://dashboard.render.com/u/settings#api-keys
#   3. Aiven password (Reveal/Copy in Aiven console)
#
# Run:
#   cd "d:\MK\Aveline BizFlow"
#   .\deploy\GO-LIVE.ps1
# =============================================================================

$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

Write-Host ""
Write-Host "=== Aveline BizFlow Production Go-Live ===" -ForegroundColor Cyan
Write-Host ""

$Config = @{
    AivenHost   = "mysql-1379bbe4-pmkrish1997-25f8.b.aivencloud.com"
    AivenPort   = "13622"
    AivenDb     = "defaultdb"
    AivenUser   = "avnadmin"
    ServiceId   = "srv-db7r4b59fdbs73f81cpg"
    ApiUrl      = "https://aveline-bizflow-api.onrender.com"
    FrontendUrl = "https://frontend-zeta-topaz-48.vercel.app"
}

Write-Host "Frontend : $($Config.FrontendUrl)"
Write-Host "API      : $($Config.ApiUrl)"
Write-Host "MySQL    : $($Config.AivenHost):$($Config.AivenPort)/$($Config.AivenDb)"
Write-Host ""

function Read-Secret([string]$prompt) {
    $secure = Read-Host $prompt -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(ValueFromRemainingArguments = $true)][string[]]$ArgumentList
    )
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $FilePath @ArgumentList 2>&1 | ForEach-Object { "$_" }
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Get-HttpText {
    param([string]$Uri, [int]$TimeoutSec = 45)
    $resp = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec $TimeoutSec
    $body = $resp.Content
    if ($body -is [byte[]]) {
        $body = [System.Text.Encoding]::UTF8.GetString($body)
    } else {
        $body = [string]$body
    }
    return [pscustomobject]@{
        StatusCode = [int]$resp.StatusCode
        Content    = $body
    }
}

if (-not $env:RENDER_API_KEY) {
    $env:RENDER_API_KEY = Read-Secret "Render API key"
}
if ([string]::IsNullOrWhiteSpace($env:RENDER_API_KEY)) {
    Write-Error "Render API key is required."
}

$dbPassword = Read-Secret "Aiven MySQL password (Reveal in Aiven console)"
if ([string]::IsNullOrWhiteSpace($dbPassword)) {
    Write-Error "Aiven password is required."
}

$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$jwt = [Convert]::ToBase64String($bytes)

$env:DB_URL = "jdbc:mysql://$($Config.AivenHost):$($Config.AivenPort)/$($Config.AivenDb)?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=UTF-8&connectionTimeZone=UTC"
$env:DB_USERNAME = $Config.AivenUser
$env:DB_PASSWORD = $dbPassword
$env:JWT_SECRET = $jwt
$env:RENDER_SERVICE_ID = $Config.ServiceId
$env:CORS_ALLOWED_ORIGINS = $Config.FrontendUrl

Write-Host ""
Write-Host "[1/3] Setting Render environment + deploying API..." -ForegroundColor Yellow
& "$PSScriptRoot\render-set-db-env.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "[2/3] Ensuring Vercel API_URL points at Render..." -ForegroundColor Yellow
Push-Location frontend
try {
    $whoamiCode = Invoke-Native npx --yes vercel whoami
    if ($whoamiCode -ne 0) {
        Write-Host "Vercel CLI not logged in - skipping frontend env update (already set earlier)."
    } else {
        $null = Invoke-Native npx --yes vercel env rm API_URL production -y
        $addOut = cmd /c "echo $($Config.ApiUrl)| npx --yes vercel env add API_URL production"
        $addOut | ForEach-Object { Write-Host $_ }
        Write-Host "API_URL confirmed for production."
        Write-Host "Triggering production frontend redeploy from Git..."
        $authPath = Join-Path $env:APPDATA "com.vercel.cli\Data\auth.json"
        if (Test-Path $authPath) {
            $auth = Get-Content $authPath -Raw | ConvertFrom-Json
            $token = $auth.token
            if (-not $token) { $token = $auth.accessToken }
            if ($token) {
                $headers = @{
                    Authorization  = "Bearer $token"
                    Accept         = "application/json"
                    "Content-Type" = "application/json"
                }
                $team = "team_znPpz50fftPxBLpF2H0nbGBO"
                $body = @{
                    name    = "frontend"
                    project = "frontend"
                    gitSource = @{
                        type = "github"
                        org  = "MuthukrishnanPerumal"
                        repo = "BizFlow"
                        ref  = "main"
                    }
                    target = "production"
                    projectSettings = @{
                        framework       = "angular"
                        buildCommand    = "npm run build"
                        outputDirectory = "dist/frontend/browser"
                        installCommand  = "npm ci"
                        rootDirectory   = "frontend"
                    }
                } | ConvertTo-Json -Depth 6
                try {
                    $dep = Invoke-RestMethod -Method Post -Uri "https://api.vercel.com/v13/deployments?teamId=$team&forceNew=1" -Headers $headers -Body $body
                    Write-Host "Frontend redeploy started: $($dep.id)"
                    for ($i = 0; $i -lt 48; $i++) {
                        Start-Sleep 5
                        $d = Invoke-RestMethod -Uri "https://api.vercel.com/v13/deployments/$($dep.id)?teamId=$team" -Headers $headers
                        Write-Host "frontend state=$($d.readyState)"
                        if ($d.readyState -eq "READY" -or $d.readyState -eq "ERROR" -or $d.readyState -eq "CANCELED") { break }
                    }
                } catch {
                    Write-Host "Frontend redeploy skipped: $($_.Exception.Message)"
                }
            }
        }
    }
} catch {
    Write-Host "Vercel step skipped (non-fatal): $($_.Exception.Message)" -ForegroundColor Yellow
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "[3/3] Verifying production endpoints..." -ForegroundColor Yellow
Start-Sleep 5

$feOk = $false
$apiOk = $false
$apiBody = ""
try {
    $fe = Get-HttpText -Uri $Config.FrontendUrl -TimeoutSec 30
    $feOk = ($fe.StatusCode -eq 200)
    Write-Host "Frontend HTTP $($fe.StatusCode)"
} catch {
    Write-Host "Frontend check failed: $($_.Exception.Message)" -ForegroundColor Red
}

for ($i = 1; $i -le 12; $i++) {
    try {
        $api = Get-HttpText -Uri "$($Config.ApiUrl)/actuator/health/readiness" -TimeoutSec 45
        $apiBody = $api.Content
        Write-Host "API readiness HTTP $($api.StatusCode) $apiBody"
        $apiOk = ($api.StatusCode -eq 200 -and $apiBody -match '"status"\s*:\s*"UP"')
        break
    } catch {
        Write-Host "API not ready yet (attempt $i/12)..."
        Start-Sleep 15
    }
}

Write-Host ""
Write-Host "===== PASTE THIS BLOCK INTO CHAT (safe, no secrets) ====="
Write-Host "Frontend URL: $($Config.FrontendUrl)"
Write-Host "API URL: $($Config.ApiUrl)"
Write-Host "Frontend OK: $feOk"
Write-Host "API readiness OK: $apiOk"
if ($apiBody) {
    Write-Host "API readiness body: $apiBody"
}
Write-Host "===== END PASTE BLOCK ====="
Write-Host ""

if ($feOk -and $apiOk) {
    Write-Host "PRODUCTION LIVE" -ForegroundColor Green
    Write-Host "App: $($Config.FrontendUrl)"
    Write-Host "API: $($Config.ApiUrl)"
    Write-Host "First login: admin / Aveline#Admin1  (change password immediately)"
    exit 0
}

if ($feOk) {
    Write-Host "Frontend live; API still failing readiness. Check Render logs." -ForegroundColor Yellow
    Write-Host "https://dashboard.render.com/web/$($Config.ServiceId)/logs"
    exit 1
}

Write-Host "Go-live checks incomplete." -ForegroundColor Red
exit 1

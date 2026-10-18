# Quick production verification (no secrets required).
#   cd "d:\MK\Aveline BizFlow"
#   .\deploy\VERIFY-PROD.ps1

$ErrorActionPreference = "Continue"
$FrontendUrl = "https://frontend-zeta-topaz-48.vercel.app"
$ApiUrl = "https://aveline-bizflow-api.onrender.com"

function Get-Text([string]$Uri, [int]$TimeoutSec = 90) {
    $resp = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec $TimeoutSec
    $body = $resp.Content
    if ($body -is [byte[]]) { $body = [System.Text.Encoding]::UTF8.GetString($body) }
    return [pscustomobject]@{ StatusCode = [int]$resp.StatusCode; Content = [string]$body }
}

Write-Host "Checking frontend..."
$feOk = $false
try {
    $fe = Get-Text $FrontendUrl 30
    $feOk = ($fe.StatusCode -eq 200)
    Write-Host "Frontend HTTP $($fe.StatusCode)"
} catch {
    Write-Host "Frontend FAIL: $($_.Exception.Message)"
}

Write-Host "Checking API readiness (cold start may take up to ~2 minutes)..."
$apiOk = $false
$apiBody = ""
for ($i = 1; $i -le 12; $i++) {
    try {
        $api = Get-Text "$ApiUrl/actuator/health/readiness" 60
        $apiBody = $api.Content
        Write-Host "API readiness HTTP $($api.StatusCode) $apiBody"
        $apiOk = ($api.StatusCode -eq 200 -and $apiBody -match '"status"\s*:\s*"UP"')
        break
    } catch {
        Write-Host "API wake attempt $i/12 failed: $($_.Exception.Message)"
        Start-Sleep 15
    }
}

Write-Host ""
Write-Host "===== PASTE THIS BLOCK INTO CHAT (safe, no secrets) ====="
Write-Host "Frontend URL: $FrontendUrl"
Write-Host "API URL: $ApiUrl"
Write-Host "Frontend OK: $feOk"
Write-Host "API readiness OK: $apiOk"
if ($apiBody) { Write-Host "API readiness body: $apiBody" }
Write-Host "===== END PASTE BLOCK ====="

if ($feOk -and $apiOk) { exit 0 }
exit 1

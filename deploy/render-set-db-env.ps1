# Sets database + JWT env vars on the Render web service, then triggers a deploy.
# Usage:
#   $env:RENDER_API_KEY = "<key>"
#   $env:DB_URL = "jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=UTF-8&connectionTimeZone=UTC"
#   $env:DB_USERNAME = "avnadmin"
#   $env:DB_PASSWORD = "<password>"
#   $env:JWT_SECRET = "<at-least-32-random-chars>"
#   .\deploy\render-set-db-env.ps1
#
# Optional:
#   $env:RENDER_SERVICE_ID = "srv-db7r4b59fdbs73f81cpg"
#   $env:CORS_ALLOWED_ORIGINS = "https://frontend-zeta-topaz-48.vercel.app"

$ErrorActionPreference = "Stop"
$key = $env:RENDER_API_KEY
if (-not $key) { Write-Error "Set RENDER_API_KEY" }

$required = @("DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET")
foreach ($n in $required) {
    if (-not [Environment]::GetEnvironmentVariable($n)) {
        Write-Error "Missing env var $n"
    }
}
if ($env:JWT_SECRET.Length -lt 32) {
    Write-Error "JWT_SECRET must be at least 32 characters"
}

$sid = $env:RENDER_SERVICE_ID
if (-not $sid) { $sid = "srv-db7r4b59fdbs73f81cpg" }

$cors = $env:CORS_ALLOWED_ORIGINS
if (-not $cors) { $cors = "https://frontend-zeta-topaz-48.vercel.app" }

$headers = @{
    Authorization  = "Bearer $key"
    Accept         = "application/json"
    "Content-Type" = "application/json"
}

$body = @(
    @{ key = "SPRING_PROFILES_ACTIVE"; value = "prod" }
    @{ key = "API_DOCS_ENABLED"; value = "false" }
    @{ key = "DB_POOL_SIZE"; value = "5" }
    @{ key = "DB_POOL_MIN_IDLE"; value = "1" }
    @{ key = "LOGIN_MAX_ATTEMPTS"; value = "20" }
    @{ key = "LOGIN_WINDOW"; value = "PT1M" }
    @{ key = "CORS_ALLOWED_ORIGINS"; value = $cors }
    @{ key = "DB_URL"; value = $env:DB_URL }
    @{ key = "DB_USERNAME"; value = $env:DB_USERNAME }
    @{ key = "DB_PASSWORD"; value = $env:DB_PASSWORD }
    @{ key = "JWT_SECRET"; value = $env:JWT_SECRET }
) | ConvertTo-Json -Depth 5

Write-Host "Updating env vars on $sid (values not printed)..."
Invoke-RestMethod -Method Put -Uri "https://api.render.com/v1/services/$sid/env-vars" -Headers $headers -Body $body | Out-Null

Write-Host "Triggering deploy..."
$deployBody = @{ clearCache = "do_not_clear" } | ConvertTo-Json
$deploy = Invoke-RestMethod -Method Post -Uri "https://api.render.com/v1/services/$sid/deploys" -Headers $headers -Body $deployBody
$depId = $deploy.id
Write-Host "Deploy id: $depId"

for ($i = 0; $i -lt 90; $i++) {
    Start-Sleep 10
    $list = Invoke-RestMethod -Uri "https://api.render.com/v1/services/$sid/deploys?limit=1" -Headers $headers
    $status = $list[0].deploy.status
    Write-Host "$(Get-Date -Format HH:mm:ss) status=$status"
    if ($status -eq "live") {
        Write-Host "Service live: https://aveline-bizflow-api.onrender.com"
        try {
            $h = Invoke-WebRequest -Uri "https://aveline-bizflow-api.onrender.com/actuator/health/readiness" -UseBasicParsing -TimeoutSec 60
            $body = $h.Content
            if ($body -is [byte[]]) { $body = [System.Text.Encoding]::UTF8.GetString($body) }
            Write-Host "readiness=$($h.StatusCode) $body"
        } catch {
            Write-Host "readiness check failed (may still be waking): $($_.Exception.Message)"
        }
        exit 0
    }
    if ($status -eq "update_failed" -or $status -eq "build_failed" -or $status -eq "canceled" -or $status -eq "deactivated") {
        Write-Error "Deploy ended with status=$status. Check https://dashboard.render.com/web/$sid"
    }
}

Write-Error "Timed out waiting for deploy"

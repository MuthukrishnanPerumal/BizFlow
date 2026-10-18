# Creates the Render Docker web service via API when RENDER_API_KEY is set.
# Usage (PowerShell):
#   $env:RENDER_API_KEY = "<from https://dashboard.render.com/u/settings#api-keys>"
#   $env:GITHUB_REPO = "MuthukrishnanPerumal/BizFlow"
#   $env:FRONTEND_ORIGIN = "https://frontend-zeta-topaz-48.vercel.app"
#   .\deploy\render-create-service.ps1
#
# Never commit real API keys. This script prints service URLs only, not secrets.
# If a key was pasted into chat or a ticket, revoke it and create a new one.

$ErrorActionPreference = "Stop"
$key = $env:RENDER_API_KEY
if (-not $key) {
    Write-Error "Set RENDER_API_KEY first (Render Dashboard → Account Settings → API Keys)."
}

$headers = @{
    Authorization  = "Bearer $key"
    Accept         = "application/json"
    "Content-Type" = "application/json"
}

Write-Host "Listing existing services..."
$services = Invoke-RestMethod -Uri "https://api.render.com/v1/services?limit=50" -Headers $headers
$existing = @($services | ForEach-Object { $_.service } | Where-Object { $_.name -eq "aveline-bizflow-api" })

$frontendOrigin = $env:FRONTEND_ORIGIN
if (-not $frontendOrigin) {
    $frontendOrigin = "https://frontend-zeta-topaz-48.vercel.app"
}

if ($existing.Count -gt 0) {
    $svc = $existing[0]
    $url = $svc.serviceDetails.url
    Write-Host "Service already exists: $($svc.id)"
    Write-Host "URL: $url"
    Write-Host "Dashboard: https://dashboard.render.com/web/$($svc.id)"
    exit 0
}

$ownerId = $env:RENDER_OWNER_ID
if (-not $ownerId) {
    $owners = Invoke-RestMethod -Uri "https://api.render.com/v1/owners?limit=20" -Headers $headers
    $ownerId = ($owners | Select-Object -First 1).owner.id
    if (-not $ownerId) { Write-Error "Could not resolve Render owner id." }
    Write-Host "Using owner: $ownerId"
}

$repo = $env:GITHUB_REPO
if (-not $repo) {
    Write-Error "Set GITHUB_REPO=owner/repo (example: MuthukrishnanPerumal/BizFlow)."
}

$envVars = @(
    @{ key = "SPRING_PROFILES_ACTIVE"; value = "prod" }
    @{ key = "API_DOCS_ENABLED"; value = "false" }
    @{ key = "DB_POOL_SIZE"; value = "5" }
    @{ key = "DB_POOL_MIN_IDLE"; value = "1" }
    @{ key = "CORS_ALLOWED_ORIGINS"; value = $frontendOrigin }
    @{ key = "LOGIN_MAX_ATTEMPTS"; value = "20" }
    @{ key = "LOGIN_WINDOW"; value = "PT1M" }
)

# Optional secrets from the shell (never logged)
foreach ($pair in @(
    @{ Key = "DB_URL"; Env = "DB_URL" },
    @{ Key = "DB_USERNAME"; Env = "DB_USERNAME" },
    @{ Key = "DB_PASSWORD"; Env = "DB_PASSWORD" },
    @{ Key = "JWT_SECRET"; Env = "JWT_SECRET" }
)) {
    $val = [Environment]::GetEnvironmentVariable($pair.Env)
    if ($val) {
        $envVars += @{ key = $pair.Key; value = $val }
    }
}

# Render API requires serviceDetails for non-static services.
$bodyObj = @{
    type     = "web_service"
    name     = "aveline-bizflow-api"
    ownerId  = $ownerId
    repo     = "https://github.com/$repo"
    branch   = "main"
    rootDir  = "backend"
    autoDeployTrigger = "commit"
    envVars  = $envVars
    buildFilter = @{
        paths        = @("backend/**")
        ignoredPaths = @("frontend/**", "deploy/**", ".github/**")
    }
    serviceDetails = @{
        runtime         = "docker"
        plan            = "free"
        region          = "oregon"
        healthCheckPath = "/actuator/health/readiness"
        envSpecificDetails = @{
            dockerfilePath = "./Dockerfile"
            dockerContext  = "."
        }
    }
}

$body = $bodyObj | ConvertTo-Json -Depth 8
Write-Host "Creating Render web service for repo $repo ..."
try {
    $created = Invoke-RestMethod -Method Post -Uri "https://api.render.com/v1/services" -Headers $headers -Body $body
    $svc = $created.service
    if (-not $svc) { $svc = $created }
    $url = $svc.serviceDetails.url
    Write-Host "Created service id: $($svc.id)"
    Write-Host "Deploy id: $($created.deployId)"
    Write-Host "Service URL: $url"
    Write-Host "Dashboard: https://dashboard.render.com/web/$($svc.id)"
    if (-not $env:DB_URL -or -not $env:JWT_SECRET) {
        Write-Host ""
        Write-Host "Next: set DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET in the Render Environment tab, then Manual Deploy."
    }
} catch {
    Write-Host "API create failed: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    exit 1
}

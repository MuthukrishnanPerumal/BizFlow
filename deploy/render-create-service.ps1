# Creates or updates the Render Docker web service via API when RENDER_API_KEY is set.
# Usage (PowerShell):
#   $env:RENDER_API_KEY = "<from https://dashboard.render.com/u/settings#api-keys>"
#   $env:GITHUB_REPO = "YOUR_USER/YOUR_REPO"   # optional until Git is connected
#   .\deploy\render-create-service.ps1
#
# Never commit real API keys. This script prints service URLs only, not secrets.

$ErrorActionPreference = "Stop"
$key = $env:RENDER_API_KEY
if (-not $key) {
    Write-Error "Set RENDER_API_KEY first (Render Dashboard → Account Settings → API Keys)."
}

$headers = @{
    Authorization = "Bearer $key"
    Accept        = "application/json"
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
    Write-Host "Service already exists: $($svc.id)"
    Write-Host "URL: https://$($svc.serviceDetails.url)"
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

# Blueprint-style create requires a connected GitHub repo on Render for auto-deploy.
# If GITHUB_REPO is unset, print the exact dashboard steps instead of inventing a URL.
$repo = $env:GITHUB_REPO
if (-not $repo) {
    Write-Host @"

No GITHUB_REPO set. Create the service in the dashboard (or re-run with GITHUB_REPO=user/repo after connecting GitHub on Render):

1. https://dashboard.render.com → New → Web Service
2. Connect the GitHub repository that contains this monorepo
3. Root Directory: backend
4. Runtime: Docker
5. Dockerfile Path: ./Dockerfile
6. Plan: Free
7. Health Check Path: /actuator/health/readiness
8. Environment variables (set in dashboard, do not put secrets here):
   SPRING_PROFILES_ACTIVE=prod
   DB_URL=...
   DB_USERNAME=...
   DB_PASSWORD=...
   DB_POOL_SIZE=5
   JWT_SECRET=...
   CORS_ALLOWED_ORIGINS=$frontendOrigin
   API_DOCS_ENABLED=false

After deploy, copy the onrender.com URL and set Vercel API_URL to that origin, then:
  cd frontend
  npx vercel env rm API_URL production -y
  echo https://YOUR-SERVICE.onrender.com | npx vercel env add API_URL production
  npx vercel deploy --prod --yes

"@
    exit 2
}

$body = @{
    type    = "web_service"
    name    = "aveline-bizflow-api"
    ownerId = $ownerId
    repo    = "https://github.com/$repo"
    branch  = "main"
    rootDir = "backend"
    runtime = "docker"
    dockerCommand = $null
    dockerfilePath = "./Dockerfile"
    plan    = "free"
    region  = "oregon"
    healthCheckPath = "/actuator/health/readiness"
    envVars = @(
        @{ key = "SPRING_PROFILES_ACTIVE"; value = "prod" }
        @{ key = "API_DOCS_ENABLED"; value = "false" }
        @{ key = "DB_POOL_SIZE"; value = "5" }
        @{ key = "DB_POOL_MIN_IDLE"; value = "1" }
        @{ key = "CORS_ALLOWED_ORIGINS"; value = $frontendOrigin }
        @{ key = "DB_URL"; generateValue = $false }
        @{ key = "DB_USERNAME"; generateValue = $false }
        @{ key = "DB_PASSWORD"; generateValue = $false }
        @{ key = "JWT_SECRET"; generateValue = $false }
    )
} | ConvertTo-Json -Depth 6

Write-Host "Creating Render web service for repo $repo ..."
try {
    $created = Invoke-RestMethod -Method Post -Uri "https://api.render.com/v1/services" -Headers $headers -Body $body
    $svc = $created.service
    if (-not $svc) { $svc = $created }
    Write-Host "Created service id: $($svc.id)"
    Write-Host "Service URL (when live): https://$($svc.serviceDetails.url)"
    Write-Host "Set DB_*, JWT_SECRET in the Render dashboard, then trigger a deploy."
} catch {
    Write-Host "API create failed: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) { Write-Host $_.ErrorDetails.Message }
    exit 1
}

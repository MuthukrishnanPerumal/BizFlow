# Copy values into your shell (do NOT commit real secrets).
# After creating a Render API key at:
#   https://dashboard.render.com/u/settings#api-keys
#
# Then run:
#   .\deploy\render-create-service.ps1

$env:RENDER_API_KEY = "<paste-api-key-here>"
$env:GITHUB_REPO = "MuthukrishnanPerumal/BizFlow"
$env:FRONTEND_ORIGIN = "https://frontend-zeta-topaz-48.vercel.app"

# Optional once Aiven exists (also set these in the Render dashboard):
# $env:DB_URL = "jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=UTF-8&connectionTimeZone=UTC"
# $env:DB_USERNAME = "avnadmin"
# $env:DB_PASSWORD = "<aiven-password>"
# $env:JWT_SECRET = "<at-least-32-random-chars>"

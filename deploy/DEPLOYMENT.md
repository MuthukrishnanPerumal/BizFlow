# Aveline BizFlow — Free-tier production deployment

## Live status (updated by deployment automation)

| Component | Status | URL |
| --- | --- | --- |
| Frontend (Vercel) | **Live** | https://frontend-zeta-topaz-48.vercel.app (also https://frontend-biz-flow8.vercel.app) |
| Backend (Render) | Not deployed yet | Requires `RENDER_API_KEY` + GitHub repo + Aiven MySQL |
| Database (Aiven MySQL) | Not provisioned yet | Account action required |

Vercel project: `biz-flow8/frontend`. Production env `API_URL` is set (currently a placeholder until Render is live). SSO deployment protection is disabled so the site is publicly reachable.

Target stack:

| Layer | Service | Plan notes |
| --- | --- | --- |
| Frontend | Vercel Hobby | Git-based production + preview deploys |
| Backend | Render Free Docker web service | Sleeps after ~15 minutes idle; ~1 minute wake; 750 instance-hours/month |
| Database | Aiven MySQL Free | Real MySQL 8; 1 GB disk / 1 GB RAM; may power off when idle; `max_connections=76` |

This guide uses Windows PowerShell. Do not commit `.env` files or real secrets.

---

## 1. One-time GitHub setup

This repository starts as a local monorepo. Create a GitHub repository, then:

```powershell
cd "d:\MK\Aveline BizFlow"
git status
git add .env.example .gitignore README.md backend deploy frontend render.yaml .github
git status
git commit -m @"
Add free-tier deployment configuration for Vercel, Render, and Aiven MySQL.
"@
git branch -M main
git remote add origin https://github.com/YOUR_GITHUB_USER/YOUR_REPO.git
git push -u origin main
```

Replace the remote URL with yours. Never add `.env` or credential files.

---

## 2. Database — Aiven MySQL Free

### Why Aiven

Migrations use MySQL 8 features (`utf8mb4_0900_ai_ci`, stored generated columns, InnoDB). Render’s free database is PostgreSQL. PlanetScale has no free tier. Do not silently switch engines.

### Limits

- 1 vCPU, 1 GB RAM, 1 GB storage
- `max_connections` = 76
- May power off after inactivity (email/in-app notice); power back on in the console
- Not covered by Aiven’s HA SLA

### Create the service

1. Sign up at [https://aiven.io](https://aiven.io) (no credit card required for free MySQL).
2. Create a project → **Create service** → **MySQL** → plan **Free**.
3. Choose a region close to your Render region.
4. Wait until the service is **Running**.
5. Open **Overview** / **Connection information**:
   - Host, port, database name (often `defaultdb`)
   - User (often `avnadmin`)
   - Password
6. Download or note that SSL/TLS is required.

### JDBC URL (example shape only)

```text
jdbc:mysql://YOUR_HOST:YOUR_PORT/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=UTF-8&connectionTimeZone=UTC
```

### Backup and rollback (before any production schema change)

1. In Aiven → service → **Backups**: confirm automatic backups are listed.
2. Before a risky change, take a logical dump from your workstation:

```powershell
# Install MySQL client if needed, then:
mysqldump --ssl-mode=REQUIRED -h YOUR_HOST -P YOUR_PORT -u avnadmin -p defaultdb > "bizflow-backup-$(Get-Date -Format yyyyMMdd-HHmmss).sql"
```

3. Flyway runs automatically on API startup and applies pending `V*` migrations only. It does not drop data by itself.
4. **Never** point a new environment at a database that still has development data you care about without a backup.
5. Rollback strategy:
   - Restore the latest Aiven backup or your `mysqldump` into a new database / restored service.
   - Redeploy the previous known-good API image/commit on Render.
   - Do not hand-edit `flyway_schema_history` unless you know exactly why.

### First boot

Create an empty schema (Aiven `defaultdb` is fine). Grant the service user rights to that schema. The first API start with `SPRING_PROFILES_ACTIVE=prod` runs Flyway V1–V9, then Hibernate validates.

Default admin (from identity migration) — change immediately after first login:

- Username: `admin`
- Password: `Aveline#Admin1`

---

## 3. Backend — Render Free Docker

### Limits

- Spins down after 15 minutes without inbound HTTP
- Cold start ~1 minute
- ~512 MB RAM / 0.1 CPU
- 750 free instance-hours per workspace per month (idle spun-down time does not consume hours)
- Ephemeral filesystem

### Dashboard setup (recommended)

1. [https://dashboard.render.com](https://dashboard.render.com) → **New** → **Web Service**.
2. Connect the GitHub repository.
3. Settings:

| Setting | Value |
| --- | --- |
| Name | `aveline-bizflow-api` (or your choice) |
| Root Directory | `backend` |
| Runtime | Docker |
| Dockerfile Path | `./Dockerfile` |
| Instance type | Free |
| Health Check Path | `/actuator/health/readiness` |

4. Environment variables (Render → Environment):

| Name | Example (not real secrets) |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:mysql://…aivencloud.com:….…/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=UTF-8&connectionTimeZone=UTC` |
| `DB_USERNAME` | `avnadmin` |
| `DB_PASSWORD` | *(from Aiven)* |
| `DB_POOL_SIZE` | `5` |
| `DB_POOL_MIN_IDLE` | `1` |
| `JWT_SECRET` | *(≥32 random characters; not the dev/test default)* |
| `JWT_ACCESS_TTL` | `PT8H` |
| `CORS_ALLOWED_ORIGINS` | `https://YOUR_PROJECT.vercel.app` |
| `API_DOCS_ENABLED` | `false` |
| `LOGIN_MAX_ATTEMPTS` | `20` |
| `LOGIN_WINDOW` | `PT1M` |

Render sets `PORT` automatically. The Dockerfile passes it to Spring Boot.

5. Deploy. Copy the public URL, for example `https://aveline-bizflow-api.onrender.com`.
6. After the first successful deploy, open:

```text
https://YOUR_RENDER_HOST/actuator/health/readiness
```

Expect HTTP 200 with a status body that does not expose credentials.

Optional: `render.yaml` at the repo root can seed a Blueprint; still set secret values in the dashboard (`sync: false`).

### GitHub auto-deploy

In the Render service → **Settings** → ensure the production branch (`main`) auto-deploys on push. Use **Manual Deploy** → **Deploy a specific commit** to roll back.

---

## 4. Frontend — Vercel Hobby

### Project settings

1. [https://vercel.com](https://vercel.com) → **Add New…** → **Project** → import the GitHub repo.
2. Configure:

| Setting | Value |
| --- | --- |
| Framework Preset | Other |
| Root Directory | `frontend` |
| Build Command | `npm run build` |
| Output Directory | `dist/frontend/browser` |
| Install Command | `npm ci` |

`frontend/vercel.json` already configures SPA rewrites so refresh on deep routes serves `index.html`.

3. Environment variables (Production and Preview as needed):

| Name | Example |
| --- | --- |
| `API_URL` | `https://aveline-bizflow-api.onrender.com` |

`npm run build` runs `scripts/prepare-production-env.mjs`, which writes `environment.production.ts` with `https://…/api/v1`. Localhost values are rejected. If `API_URL` is unset, the build keeps same-origin `/api/v1` (nginx mode only).

4. Production branch: `main` (or your chosen branch). Other branches get Preview deployments.
5. After the first frontend deploy, update Render `CORS_ALLOWED_ORIGINS` to the exact Vercel origin(s), including custom domains, then redeploy the API.

### Optional: skip frontend build when only backend changed

Vercel → Project → Settings → Git → **Ignored Build Step**:

```bash
git diff --quiet HEAD^ HEAD -- ./
```

Or use Vercel’s path filter UI so builds run only when `frontend/**` changes.

---

## 5. Wire the three services together

Order that avoids CORS and empty-API mistakes:

1. Create Aiven MySQL and copy connection values.
2. Deploy Render API with DB + JWT + a temporary CORS origin (or your expected Vercel URL).
3. Confirm readiness: `GET /actuator/health/readiness`.
4. Deploy Vercel with `API_URL` = Render origin.
5. Set `CORS_ALLOWED_ORIGINS` to the real Vercel URL(s) and redeploy Render.
6. Open the Vercel site, sign in, change the admin password, exercise a business flow.

---

## 6. Local verification (Windows PowerShell)

### Frontend production build

```powershell
cd "d:\MK\Aveline BizFlow\frontend"
npm ci
$env:API_URL = "https://example-api.onrender.com"
npm run build
# Output: dist\frontend\browser
Select-String -Path "dist\frontend\browser\*.js" -Pattern "localhost:8080" -SimpleMatch
# Expect no matches
```

### Backend package

```powershell
cd "d:\MK\Aveline BizFlow\backend"
mvn -B -DskipTests package
# JAR: target\bizflow-server-0.1.0.jar
```

### Docker image

Start Docker Desktop first (Linux engine). Then:

```powershell
cd "d:\MK\Aveline BizFlow\backend"
docker build -t aveline-bizflow-api:local .
```

If `docker build` fails with `dockerDesktopLinuxEngine` / pipe errors, the daemon is not running.

### Run container against a real DB (only with your secrets in the shell, never in git)

```powershell
$env:SPRING_PROFILES_ACTIVE = "prod"
# Set DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET, CORS_ALLOWED_ORIGINS in the shell first.
docker run --rm -p 8080:8080 `
  -e SPRING_PROFILES_ACTIVE=prod `
  -e PORT=8080 `
  -e DB_URL `
  -e DB_USERNAME `
  -e DB_PASSWORD `
  -e DB_POOL_SIZE=5 `
  -e JWT_SECRET `
  -e CORS_ALLOWED_ORIGINS="http://localhost:4200" `
  -e API_DOCS_ENABLED=false `
  aveline-bizflow-api:local
```

Then:

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health/readiness
```

---

## 7. Automatic deployments

| Change location | What deploys |
| --- | --- |
| Push to production branch | Vercel (frontend) and Render (backend) via their GitHub apps |
| Pull request / other branch | Vercel Preview (frontend). Render follows your service branch settings |
| `.github/workflows/ci.yml` | Build checks only — does **not** deploy and holds no deploy credentials |

Do not add deploy tokens to workflow files. Put secrets in Vercel, Render, and Aiven only.

### Failed deployment recovery

1. Read the failing build log (Vercel or Render).
2. Fix the commit and push, or redeploy the last known-good commit/image.
3. If Flyway failed mid-way, restore DB from backup before retrying a destructive fix.
4. If the API crash-loops on `ProductionSecurityGuard`, fix env vars (`JWT_SECRET`, `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS`) — do not weaken security filters.

### Rollback

- **Vercel:** Deployments → prior deployment → **Promote to Production**.
- **Render:** Manual Deploy → previous commit.
- **Database:** Restore Aiven backup / `mysqldump`; redeploy matching API version.

---

## 8. Production testing checklist

- [ ] Aiven MySQL is Running; TLS JDBC URL works
- [ ] Render deploy succeeded; `/actuator/health/readiness` returns UP
- [ ] `/actuator/health/liveness` returns UP
- [ ] Vercel production deploy succeeded
- [ ] Hard refresh / direct navigation to a deep Angular route works (SPA rewrite)
- [ ] Browser network calls go to `https://…onrender.com/api/v1/...`, not `localhost`
- [ ] Login works; forced password change works
- [ ] A protected API call succeeds (CORS + JWT)
- [ ] OpenAPI UI is disabled on the public host (`API_DOCS_ENABLED=false`)
- [ ] No secrets in git (`git grep` / GitHub secret scanning)
- [ ] After ~20 minutes idle, first request may be slow (Render and/or Aiven wake)

---

## 9. Troubleshooting

| Symptom | Likely cause | What to do |
| --- | --- | --- |
| Frontend calls `/api/v1` on Vercel and 404s | `API_URL` missing at build time | Set Vercel env `API_URL`, redeploy frontend |
| Browser CORS error | `CORS_ALLOWED_ORIGINS` mismatch | Use exact origin (`https://….vercel.app`), no trailing slash, redeploy API |
| API exits on startup: JWT / CORS / DB_PASSWORD | `ProductionSecurityGuard` | Set strong `JWT_SECRET`, non-blank `DB_PASSWORD`, explicit CORS origins |
| Readiness fails | DB URL/TLS/user/password, or Aiven powered off | Power on Aiven; verify JDBC URL `sslMode=REQUIRED` |
| Flyway checksum / migration error | Schema drift or partial migrate | Restore backup; do not edit history casually |
| Render deploy OOM / crash | Free 512 MB too small under load | Lower traffic; consider paid instance |
| Slow first request | Free-tier sleep | Wait for wake; optional external uptime ping (uses instance hours) |
| `utf8mb4_0900_ai_ci` errors | Not MySQL 8 | Use Aiven MySQL, not MariaDB/Postgres |

---

## 10. Environment variable quick reference

### Render (runtime)

`SPRING_PROFILES_ACTIVE`, `PORT` (platform), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE`, `DB_POOL_MIN_IDLE`, `JWT_SECRET`, `JWT_ACCESS_TTL`, `CORS_ALLOWED_ORIGINS`, `API_DOCS_ENABLED`, `LOGIN_MAX_ATTEMPTS`, `LOGIN_WINDOW`, `REVOKED_TOKEN_CLEANUP_MS`

### Vercel (build-time)

`API_URL` — Render HTTPS origin, e.g. `https://aveline-bizflow-api.onrender.com`

### Never commit

Real passwords, JWT secrets, Aiven connection strings with embedded credentials, `.env` files.

# Fix Aiven: "User should verify their email before login"

GitHub can show your email as verified while Aiven still blocks you. Aiven keeps its **own** unverified user record for `pmkrish1997@gmail.com`.

Request ID example: `hbqnuhw6qh6yw6fo`

## Fix Aiven (preferred)

1. Open Gmail for `pmkrish1997@gmail.com`.
2. Search: `from:aiven` or `subject:verify` or `subject:confirm` or `subject:Aiven`.
3. Also check **Spam**, **Promotions**, and **All Mail**.
4. Open the Aiven mail and click **Verify / Confirm email**.
5. Close all Aiven tabs. Open a **private/incognito** window.
6. Go to https://console.aiven.io/login (do not reuse an old `?code=` URL).
7. Prefer **Continue with Google** using the same Gmail (often cleaner than GitHub for this error).
8. If still blocked: on https://console.aiven.io/signup create a **password** account for the same email, then verify the new Aiven mail and log in.

If no Aiven mail arrives within 15 minutes:
- Add `noreply@aiven.io` / `aiven.io` to contacts and retry signup.
- Contact Aiven with the request ID from the error toast.

## After login

1. Create service → **MySQL** → plan **Free**.
2. Copy host, port, database, user, password (TLS required).
3. In PowerShell (do not paste secrets into chat):

```powershell
cd "d:\MK\Aveline BizFlow"
$env:RENDER_API_KEY = "<your-render-api-key>"
$env:DB_URL = "jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&serverTimezone=UTC&characterEncoding=UTF-8&connectionTimeZone=UTC"
$env:DB_USERNAME = "avnadmin"
$env:DB_PASSWORD = "<password>"
# JWT_SECRET is already on Render; include again only if you want to replace it
$env:JWT_SECRET = "<at-least-32-chars>"
.\deploy\render-set-db-env.ps1
```

## Fallback if Aiven stays blocked

Use another free MySQL 8 host that allows remote SSL connections (for example Hostim free MySQL), then run the same `render-set-db-env.ps1` with that host’s JDBC URL.

Do **not** switch the app to PostgreSQL or MariaDB without migration review — this project uses MySQL 8 collations (`utf8mb4_0900_ai_ci`) and stored generated columns.

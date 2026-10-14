# Aveline BizFlow

Purchase, sales, inventory, and business management system. The API enforces authorization on the server. The Angular client is a presentation layer and cannot grant a permission the account does not have.

## Requirements

- Java 17
- Maven 3.9+
- Node.js 20.19+ or 22.12+ or 24+
- MySQL 8+

## Configuration

Settings come from the process environment. The `dev`, `test`, and `prod` profiles are separate. Production has no default database password, JWT secret, or CORS origin. Do not commit `.env` files. `.env.example` lists the names only.

| Variable | Purpose |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev`, `test`, or `prod` |
| `DB_URL` | JDBC URL |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | HMAC secret, at least 32 characters |
| `JWT_ACCESS_TTL` | Access-token lifetime, default `PT8H` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins |
| `API_DOCS_ENABLED` | OpenAPI UI. Default on in development, off in production |
| `LOGIN_MAX_ATTEMPTS` | Sign-in attempts allowed per address per window |
| `LOGIN_WINDOW` | Sign-in window, default `PT1M` |

Development:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "<mysql-password>"
```

Production startup refuses the development and test JWT secrets, a low-entropy secret, a wildcard CORS origin, and a blank database password. The failure message does not print the secret.

## Database

Flyway applies `backend/src/main/resources/db/migration` when the API starts. Hibernate validates the schema and does not create or alter tables. Monetary columns are `DECIMAL(19, 4)`. Document numbers are unique. Stock changes lock the inventory bucket inside the posting transaction.

Create an empty MySQL database for production and grant the application user rights to that schema. The first boot migrates it. Do not point production at the development database.

## Backend

```powershell
cd backend
mvn test
mvn -DskipTests package
java -jar target/bizflow-server-0.1.0.jar
```

Set `SPRING_PROFILES_ACTIVE=prod` and the variables above before the production command. The process listens on `SERVER_PORT` (default `8080`) and shuts down gracefully.

Readiness: `GET /actuator/health/readiness` checks the process and the database. Liveness: `GET /actuator/health/liveness`. Details stay hidden. Logs go to standard output and omit passwords, tokens, and secret values.

OpenAPI is served at `/swagger-ui.html` when `API_DOCS_ENABLED=true`. Leave it false on a public host. The specification documents bearer authentication, request and response models, standard errors, and the authority required by each protected operation.

## Sign-in

The identity migration creates an administrator:

- Username: `admin`
- Password: `Aveline#Admin1`

The account must change that password before it can use the business API. Passwords are stored as BCrypt hashes. Repeated failures lock the account. Repeated attempts from one address receive HTTP 429 without recording the password. Logout revokes the token identifier. Expired revocations are deleted on a schedule.

## Frontend

```powershell
cd frontend
npm ci
npm test
npm run build
```

`npm start` serves the development client at `http://localhost:4200` and calls `http://localhost:8080/api/v1`. A production build without `API_URL` uses same-origin `/api/v1` (nginx). For Vercel + Render, set build-time `API_URL` to the API HTTPS origin so the client never calls localhost. Publish `frontend/dist/frontend/browser`. `deploy/nginx.conf` remains available for same-host reverse-proxy installs.

Free-tier cloud deployment (Vercel, Render Docker, Aiven MySQL): see [deploy/DEPLOYMENT.md](deploy/DEPLOYMENT.md).

## Authorization

Every business route requires a valid access token. Method security checks permissions such as `SALES_CREATE` and `PAYMENT_CANCEL`. Those authorities are loaded from the database for the current session version. Changing a role or signing out invalidates older tokens. A request that only changes the Angular route or a stored permission list does not change what the API allows.

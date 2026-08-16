# LinkSentry

Explainable phishing URL analysis. Paste a suspicious link and LinkSentry explains
what looks wrong about it — which domain is actually registered, where a familiar
brand name is hiding, and which patterns phishing links tend to share.

```text
https://login.vietcombank.com.vn.evil-domain.xyz/account
```

The registered domain here is `evil-domain.xyz`. `vietcombank.com.vn` appears only
inside the subdomain, where anyone can put anything. Explaining that clearly, and
in a way a non-expert can act on, is the whole point of the product.

## Status: scaffold

This repository is a **runnable foundation**, not a finished product. The frontend
shell, the API, the health endpoint, the security baseline, the error contract, the
build and the CI all work. The analyzer does not exist yet.

| Working                                            | Deliberately not implemented                        |
| -------------------------------------------------- | --------------------------------------------------- |
| React shell: home, methodology, 404, navigation     | URL parsing and normalisation                        |
| `GET /api/v1/health` + live health widget           | Registrable-domain extraction (Public Suffix List)   |
| Stateless Spring Security, configured CORS          | Detection rules, risk scoring, thresholds            |
| Global error envelope with a `traceId`              | `POST /api/v1/scans`                                 |
| Framework-free domain contracts                     | Scanner submission and result UI                     |
| PostgreSQL + Flyway wired (no migrations yet)        | Scan history persistence                             |
| 32 backend tests, 17 frontend tests, 2 CI workflows | Authentication, rate limiting                        |

The analyzer is left unbuilt on purpose: it is the part worth writing by hand.
[`docs/MANUAL_IMPLEMENTATION_GUIDE.md`](docs/MANUAL_IMPLEMENTATION_GUIDE.md) is a
ten-exercise plan for building it, in order, with tests.

**The UI shows no fabricated results.** The scanner input on the home page is a
genuinely disabled preview, labelled as such.

## Security boundary

LinkSentry analyses URLs **as text only**. It never visits a submitted URL,
resolves its DNS, follows its redirects, downloads its content, renders it in an
iframe, or turns it into a clickable link. That is what keeps the service from
becoming an SSRF tool aimed by whoever fills in the form.

The consequence is stated everywhere it matters: **a low score is not evidence that
a link is safe.** Full rationale in
[`docs/SECURITY_BOUNDARY.md`](docs/SECURITY_BOUNDARY.md) and
[ADR 0001](docs/adr/0001-static-analysis-only.md).

## Prerequisites

| Tool           | Version                | Notes                                              |
| -------------- | ---------------------- | -------------------------------------------------- |
| JDK            | **25** (LTS)           | Matches the Gradle toolchain                        |
| Node.js        | **20.19+**, 24 recommended | npm 10+                                        |
| Docker         | with Compose v2+       | For PostgreSQL                                      |
| Gradle         | not required           | Use the wrapper (`./gradlew`)                       |

## Quick start

Three terminals. Database, API, then frontend.

### 1. Start PostgreSQL

```bash
cp .env.example .env          # optional; compose has local-only defaults
docker compose up -d db
docker compose ps             # wait until the db service is healthy
```

### 2. Start the backend

```bash
cd backend
./gradlew bootRun             # Windows: .\gradlew.bat bootRun
```

Verify it:

```bash
curl http://localhost:8080/api/v1/health
# {"status":"UP","service":"linksentry-api"}
```

- API: <http://localhost:8080>
- Actuator health: <http://localhost:8080/actuator/health>
- OpenAPI UI (local profile): <http://localhost:8080/swagger-ui.html>

The backend needs PostgreSQL to start, because Spring Data JPA validates its
connection at boot. If step 1 was skipped, startup fails with a connection error.

### 3. Start the frontend

```bash
cd frontend
cp .env.example .env.local    # optional; defaults to http://localhost:8080
npm install
npm run dev
```

Open <http://localhost:5173>. The "Service status" card should read **UP**. Stop the
backend and it flips to **Offline** with a retry button — that path is covered by
tests.

### Run the whole stack in Docker

```bash
docker compose --profile backend up -d --build
```

### Troubleshooting

**`ports are not available: ... bind: 127.0.0.1:5432`** — a PostgreSQL instance is
already running on the host. Put the container on a different port instead of
stopping it:

```bash
POSTGRES_PORT=55432 docker compose up -d db
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:55432/linksentry ./gradlew bootRun
```

On Windows PowerShell, set the variables first:

```powershell
$env:POSTGRES_PORT = "55432"
docker compose up -d db
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:55432/linksentry"
cd backend; .\gradlew.bat bootRun
```

**Backend fails at startup with a connection error** — PostgreSQL is not running.
Spring Data JPA validates its connection at boot, so step 1 is not optional.

**Health card shows Offline** — check the backend is on port 8080 and that
`VITE_API_BASE_URL` matches. The origin must also appear in
`CORS_ALLOWED_ORIGINS`, or the browser blocks the response even though the server
returned 200.

## Commands

### Frontend (`cd frontend`)

| Command                | Purpose                                       |
| ---------------------- | --------------------------------------------- |
| `npm install`          | Install dependencies                          |
| `npm run dev`          | Dev server on port 5173                       |
| `npm run format:check` | Verify Prettier formatting                    |
| `npm run format`       | Apply Prettier formatting                     |
| `npm run lint`         | ESLint                                        |
| `npm run typecheck`    | `tsc --noEmit`                                |
| `npm run test`         | Vitest in watch mode                          |
| `npm run test:run`     | Vitest once (what CI runs)                    |
| `npm run build`        | Type check, then production build to `dist/`  |
| `npm run preview`      | Serve the production build locally            |

### Backend (`cd backend`)

| Command                    | Purpose                                        |
| -------------------------- | ---------------------------------------------- |
| `./gradlew bootRun`        | Run the API (needs PostgreSQL)                 |
| `./gradlew test`           | Run all tests (no Docker needed — H2)          |
| `./gradlew build`          | Compile, test, and assemble the boot jar       |
| `./gradlew bootJar`        | Assemble the executable jar only               |
| `./gradlew clean`          | Delete build output                            |

On Windows use `.\gradlew.bat`.

### Infrastructure (repository root)

| Command                                     | Purpose                          |
| ------------------------------------------- | -------------------------------- |
| `docker compose config`                     | Validate the compose file        |
| `docker compose up -d db`                   | Start PostgreSQL                 |
| `docker compose logs -f db`                 | Tail database logs               |
| `docker compose down`                       | Stop containers, keep the volume |
| `docker compose down -v`                    | Stop and delete the volume       |

## Repository layout

```text
.
├── backend/            Spring Boot API (Gradle Kotlin DSL)
├── frontend/           React + TypeScript client (Vite)
├── docs/               Architecture, API contract, security boundary, ADRs, exercises
├── compose.yaml        PostgreSQL (+ optional backend) for local development
└── .github/workflows/  Frontend and backend CI
```

Backend packages follow package-by-feature with a framework-free domain at the
centre; the frontend is feature-first. Both are laid out in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Pinned versions

Everything below is pinned in `backend/build.gradle.kts`,
`backend/gradle/wrapper/gradle-wrapper.properties`, `frontend/package.json` and
`frontend/package-lock.json`.

### Backend

| Component            | Version                                    |
| -------------------- | ------------------------------------------ |
| Java toolchain       | 25 (LTS)                                   |
| Gradle               | 9.1.0 (wrapper)                            |
| Spring Boot          | 4.1.0                                      |
| springdoc-openapi    | 3.1.0                                      |
| Flyway               | managed by Spring Boot                     |
| PostgreSQL driver    | managed by Spring Boot                     |
| Testcontainers       | 1.21.3                                     |
| PostgreSQL (Docker)  | 17-alpine                                  |

### Frontend

| Component            | Version  |
| -------------------- | -------- |
| React / React DOM    | 19.2.8   |
| React Router         | 8.3.0    |
| TanStack Query       | 5.101.4  |
| Axios                | 1.19.0   |
| Zod                  | 4.4.3    |
| Vite                 | 8.2.1    |
| TypeScript           | 6.0.3    |
| Tailwind CSS         | 4.3.3    |
| Vitest               | 4.1.10   |
| ESLint               | 10.8.1   |
| Prettier             | 3.9.6    |

Two version choices are worth explaining:

- **Java 25, not 21.** The repository and CI use JDK 25. Changing it is one line
  in `backend/build.gradle.kts`.
- **TypeScript 6.0.x, not 7.** TypeScript 7 is released, but `typescript-eslint`
  still declares a peer range of `<6.1.0`. Pinning to 6.0.x keeps lint and type
  checking working together. Revisit when typescript-eslint supports 7.

Both are recorded with the other deviations in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md#7-deviations-from-the-original-scaffold-specification).

## Dependency update policy

- **Lockfiles are committed** — `frontend/package-lock.json` and the Gradle wrapper
  checksum. CI runs `npm ci`, which installs the lockfile exactly and fails on
  drift.
- **Review monthly.** `npm outdated` and `./gradlew dependencyUpdates` (once the
  versions plugin is added) show what has moved.
- **Patch and minor** updates go in one commit per ecosystem, gated on CI passing.
- **Major** updates go in their own commit with the migration notes read first.
  Spring Boot majors in particular relocate packages — Boot 4 moved
  `@WebMvcTest` and switched to Jackson 3.
- **Security advisories** are applied immediately. `npm audit` runs locally; enable
  Dependabot alerts on the repository.
- **Anything new needs a reason.** Prefer the platform. The Public Suffix List
  (Exercise 3) is the one place where a dependency is genuinely better than
  hand-rolling, because the data changes independently of our code.

## Configuration

No secrets are committed. Every `.env.example` contains local-only placeholders.

| File                     | Purpose                                     |
| ------------------------ | ------------------------------------------- |
| `.env.example`           | Compose: database name, user, password, ports |
| `backend/.env.example`   | Datasource, profile, CORS origins            |
| `frontend/.env.example`  | `VITE_API_BASE_URL`                          |

Backend configuration is environment-overridable — see
`backend/src/main/resources/application.yml`. CORS origins are validated at
startup, and a wildcard origin is never used.

Anything in a `VITE_`-prefixed variable ships to the browser in plain text. Never
put a secret in `frontend/.env*`.

## Documentation

| Document                                                                     | Contents                                        |
| ---------------------------------------------------------------------------- | ----------------------------------------------- |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md)                                      | Layering, dependency rules, future domain design |
| [API_CONTRACT.md](docs/API_CONTRACT.md)                                      | Implemented and planned endpoints                |
| [SECURITY_BOUNDARY.md](docs/SECURITY_BOUNDARY.md)                            | What the system may and may not do with input    |
| [MANUAL_IMPLEMENTATION_GUIDE.md](docs/MANUAL_IMPLEMENTATION_GUIDE.md)        | The ten build exercises, in order                |
| [ADR 0001](docs/adr/0001-static-analysis-only.md)                            | Why analysis never touches the network           |
| [ADR 0002](docs/adr/0002-explainable-rule-engine.md)                         | Why rules, not a model                           |

## Where to start

[Exercise 1](docs/MANUAL_IMPLEMENTATION_GUIDE.md#exercise-1--url-input-validation):
URL input validation. Small, self-contained, and it forces the decisions the rest
of the analyzer depends on.

## License

[MIT](LICENSE). A portfolio project — not a substitute for a real phishing
protection service.

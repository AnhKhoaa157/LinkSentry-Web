# LinkSentry

LinkSentry is an **explainable URL risk analysis** system. A user submits a URL as text; the backend normalizes that text, runs deterministic static rules, calculates a score, and returns the concrete signals found in the URL.

For example:

```text
https://login.vietcombank.com.vn.evil-domain.xyz/account
```

The registrable domain in this example is `evil-domain.xyz`. The string `vietcombank.com.vn` is only part of the subdomain, which the owner of `evil-domain.xyz` can choose freely. This is one of LinkSentry's main insights: show the user the domain that is actually registered instead of relying on a brand name at the beginning of a hostname.

> **AI currently used:** DeepSeek, only for explaining an existing result. DeepSeek does not calculate the score, choose the risk level, or receive the raw URL. Production scoring remains a deterministic Java rule engine. The `ml/` directory contains a separate local machine-learning pipeline for research; it is not connected to production scoring.

## Current status

| Component | Status | Notes |
| --- | --- | --- |
| Static URL analysis | Implemented | Text-only; no fetch, DNS lookup, or redirect following |
| Explainable rule engine | Implemented | 11 rules; each finding includes its points and explanation |
| IDNA/Unicode normalization | Implemented | ICU4J UTS #46 with Unicode/UTS #46 data `17.0.0` |
| Device licensing | Implemented | No end-user account; licenses are granted by an administrator |
| M6 persistent trial quota | Implemented in the current code | At most 3 scans per 24 hours for each unlicensed device |
| Private scan history | Implemented | Devices under the same license share retained history; default retention is 30 days |
| Browser extension | Implemented | Chrome/Edge Manifest V3, local-first popup |
| DeepSeek advisory | Optional, disabled by default | Available only for retained scans from licensed devices |
| Local ML toolkit | Implemented independently | Offline train/evaluate/predict; does not affect production scores |

This README describes the current source code, including the M6 V6 migration and persistent quota keyed by `device_id`. Some M6 decision-package documents still contain historical `PROPOSED` or `DECISION_REQUIRED` labels. If those documents are used as an operational runbook, their labels and wire contracts must be synchronized separately.

## 1. Purpose and security boundary

### 1.1 What LinkSentry does

LinkSentry answers this question:

> “What lexical signals in this URL deserve further inspection?”

It does not answer this question:

> “Is the destination website definitely safe?”

The product therefore uses language such as:

- “No strong lexical risk signals were detected.”
- “This URL contains signals that deserve further review.”
- “A low score is not evidence that the link is safe.”

The product **never** presents `safe`, `trusted`, `verified`, `phishing`, or `malicious` as a definitive verdict about a URL.

### 1.2 What the backend never does

| Never do this | Reason |
| --- | --- |
| Visit the submitted URL | Prevent the backend from becoming an SSRF proxy |
| Resolve DNS | Avoid leaking the URL under analysis and avoid timing/oracle probes |
| Follow redirects | Do not chase a different destination or trigger side effects |
| Download HTML, scripts, images, or files | Keep malware bytes out of the infrastructure |
| Execute JavaScript from the destination | Prevent arbitrary code execution |
| Render the URL in an iframe | Prevent clickjacking and attacker-controlled content |
| Create a clickable link to the submitted URL | Prevent one accidental click from breaking the entire boundary |
| Call a threat-intelligence API using the URL | Do not disclose the URL to a third party |
| Log the raw URL, query, or fragment | Queries may contain tokens, PII, or credentials |

The URL is treated as **text** from beginning to end. Even when DeepSeek is enabled, the URL is not sent to DeepSeek; URL analysis and AI explanation are separate flows.

### 1.3 Sensitive-data rules

- Reject empty input and input longer than `2048` characters before parsing.
- Accept only the `http` and `https` schemes; reject `javascript:`, `data:`, `file:`, `ftp:`, and all other schemes.
- Reject embedded credentials such as `https://user:password@example.com`.
- Keep raw input only for the minimum analysis scope. UI, logs, database records, and evidence use redacted representations.
- Preserve query and fragment only as `queryPresent` and `fragmentPresent` booleans; never return or persist their contents.
- Preserve an explicit port in the display value, including explicit default ports `80` and `443`.
- Persisted history contains only a safe response snapshot: redacted display value, normalized public fields, score, risk level, findings, engine version, timestamp, and owner license UUID.
- Return a device credential only once during bootstrap; persist only its SHA-256 hash in the database.
- Store administrator passwords as BCrypt hashes and store administrator session tokens only as SHA-256 hashes.

## 2. Standards, protocols, and technical decisions

### 2.1 URL and hostname processing

| Area | LinkSentry implementation |
| --- | --- |
| URI parsing | Use `java.net.URI`, then explicitly validate authority, host, and port; the parser alone is not treated as a complete URL policy |
| Scheme | Allow-list containing only `http` and `https` |
| Input limit | Maximum 2048 characters at the API and normalization boundaries |
| IDNA | ICU4J `78.3`, UTS #46 API, Unicode/UTS #46 data `17.0.0` |
| IDNA policy | `USE_STD3_RULES`, `CHECK_BIDI`, `CHECK_CONTEXTJ`, `CHECK_CONTEXTO`, and non-transitional ASCII/Unicode conversion; reject every `IDNA.Info` error |
| Registrable domain | Guava `InternetDomainName` with the bundled Public Suffix List; do not assume the last two labels are the registrable domain |
| IP classification | Local table based on the IANA IPv4/IPv6 Special-Purpose Address Registries; the code pins `iana-special-purpose-2025-10-09` |
| Time format | UTC ISO-8601 timestamps in the API; backend retention and quota use `Instant` and `Clock` |

### 2.2 API and persistence

- The API is REST/HTTP JSON under `/api/v1`.
- OpenAPI UI and the OpenAPI document are generated through `springdoc-openapi`; they should be enabled only in a local profile.
- HTTP DTOs are separate from domain records; `NormalizedUrl` must not be serialized directly.
- Scan, device, and license identifiers are opaque UUIDs; they are not used as rate-limit identities.
- Database migrations use append-only Flyway migrations; production schema is PostgreSQL.
- PostgreSQL is the source of truth for scan history, devices/licenses, and M6 trial events.
- H2 is used only for context or test profiles. SQL, migrations, and locking behavior must be tested on PostgreSQL Testcontainers.

### 2.3 Application-security principles

- Spring Security is stateless and cookie-free; there is no form login and no HTTP Basic authentication.
- CSRF is disabled because the API does not use cookie sessions. If cookie-based sessions are introduced, CSRF must be enabled again.
- CORS accepts only exact configured origins; wildcard origins are not used.
- Device authentication uses `Authorization: Device <credential>`.
- The administrator browser session uses `Authorization: Bearer <opaque-token>`; the token is not a JWT.
- `ADMIN_API_KEY` is an operator-only fallback for `/api/v1/admin/**` and is never shipped to the frontend.
- Rate-limit identity uses the server-observed `HttpServletRequest.getRemoteAddr()` for rate-limit buckets; the service does not trust `X-Forwarded-For`, `Forwarded`, or client-supplied headers.
- Spring Security keeps default security headers such as `X-Frame-Options: DENY` and `nosniff`.

### 2.4 Extension and AI protocols

- The extension uses Chrome/Edge Manifest V3.
- The extension requests only `activeTab` and `storage`; it does not use `tabs`, `scripting`, `webRequest`, `cookies`, content scripts, or a background service worker.
- DeepSeek is called through its HTTPS OpenAI-compatible Chat Completions API using the JDK `java.net.http.HttpClient`; no DeepSeek SDK is added.
- The AI request is synchronous, non-streaming, single-attempt, and has a 20-second timeout with no retry.
- DeepSeek receives JSON mode (`response_format: {"type":"json_object"}`); the backend strictly validates the response before returning it to the client.

The security and architecture decisions are recorded in [ADR 0001](docs/adr/0001-static-analysis-only.md), [ADR 0002](docs/adr/0002-explainable-rule-engine.md), [ADR 0003](docs/adr/0003-deterministic-brand-lookalike-detection.md), [ADR 0005](docs/adr/0005-deepseek-scan-explanation-integration.md), [ADR 0008](docs/adr/0008-device-license-authentication.md), [ADR 0009](docs/adr/0009-idna-uts46-icu4j-decision.md), and [ADR 0010](docs/adr/0010-persistent-device-scoped-trial-quota.md).

## 3. Overall architecture

```text
┌──────────────────────┐
│ Web React + TypeScript│
│ or MV3 Extension      │
└──────────┬───────────┘
           │ HTTP/JSON
           │ Authorization: Device ...
           v
┌──────────────────────────────────────────────┐
│ Spring Boot REST API                         │
│                                              │
│  Security/CORS/Rate limit/Trial quota        │
│                │                             │
│                v                             │
│  ScanService -> UrlAnalyzer                  │
│                   │                          │
│      Normalizer -> 11 Rules -> Scorer        │
│                   │                          │
│             ScanResponse                     │
│                │                             │
│       Licensed device only                   │
│                v                             │
│       Scan history / license scope           │
└──────────┬───────────────────────┬───────────┘
           │                       │
           v                       v
┌──────────────────────┐  ┌──────────────────────┐
│ PostgreSQL + Flyway   │  │ DeepSeek (optional)  │
│ history/license/M6    │  │ explanation only    │
└──────────────────────┘  └──────────────────────┘
```

### 3.1 Backend package-by-feature

```text
backend/src/main/java/com/lyanhkhoa/linksentry/
├── analysis/       normalization, domain objects, 11 rules, scoring
├── scan/           POST/GET scan use case and HTTP DTOs
├── history/        safe snapshot, owner filtering, retention
├── license/        device bootstrap, license, assignment, credential
├── admin/          administrator browser login/session
├── explanation/    DeepSeek port, service, provider adapter
├── common/security stateless security, CORS, authentication filters
├── common/ratelimit per-address token buckets
├── common/trial    M6 persistent device-scoped trial quota
├── common/config   typed configuration properties
└── health/         stable liveness endpoint
```

Dependency direction:

```text
Controller/API -> Application service -> Framework-free domain
JPA/Flyway/HTTP adapters -> Domain contracts
```

Rules in `analysis.domain` and `analysis.rules` must not import Spring, JPA, Jackson, or an HTTP client. A rule is a deterministic function with no I/O and no mutable shared state. The frontend is feature-first; server state belongs to TanStack Query and local UI state belongs to React component state.

## 4. Detailed operational flows

### Flow A - First web or extension startup

1. `LicenseProvider` checks for a credential in `localStorage` on the web or `chrome.storage.local` in the extension.
2. If no credential exists, the client calls `POST /api/v1/devices` with an optional `clientLabel` such as `web` or `extension`.
3. The backend creates a `device_installation`, a public activation code, and a high-entropy random credential.
4. The backend returns the credential exactly once. The client stores it immediately but never renders, logs, or sends the credential to the user.
5. Later requests include:

   ```http
   Authorization: Device <device-credential>
   ```

6. `GET /api/v1/devices/me` returns the current state: `PENDING`, `LICENSED`, `EXPIRED`, or `REVOKED`.

If the server no longer recognizes the credential (`INVALID_DEVICE_CREDENTIAL`), the client deletes it and bootstraps a new installation. If the device is merely pending, expired, or revoked, the client **does not** delete the credential because it remains the valid identity of that installation.

### Flow B - Administrator grants a license

```text
User opens the License page
        │
        ├─ copies activationCode
        v
Administrator signs in to /admin
        │  or an operator uses ADMIN_API_KEY
        v
Create license -> Grant activationCode -> device receives LICENSED authority
        │
        v
Web/extension refreshes GET /api/v1/devices/me
```

Detailed sequence:

1. The administrator creates a license with a label, optional expiry, and `maxDevices` (default `2`, commonly one web installation and one extension installation).
2. The administrator receives an activation code from the user and grants that code to the license.
3. `DeviceAuthenticationFilter` checks the credential on every request. Only an active assignment with a non-expired, non-revoked license creates a `LicensedDeviceContext`.
4. An activation code is only a public lookup code; copying it does not grant authority. Authority appears only after the server records the assignment.
5. The administrator can extend expiry, revoke the entire license, or revoke one device without affecting other devices.

There are two separate administrator mechanisms:

- `/admin` in the frontend uses the administrator username/password bootstrapped through `ADMIN_BOOTSTRAP_USERNAME` and `ADMIN_BOOTSTRAP_PASSWORD`. The browser session is an opaque bearer token stored in `admin_session` as a hash.
- `X-Admin-Api-Key` with `ADMIN_API_KEY` is reserved for operator automation and curl; the frontend never knows this key.

### Flow C - M6: persistent device-scoped trial quota

M6 separates two concepts:

- **Authentication/entitlement:** whether the device currently has an active license.
- **Trial admission:** whether an unlicensed device still has trial scans available.

`POST /api/v1/scans` passes through these steps:

1. CORS handles the origin and preflight request.
2. `RateLimitFilter` obtains the server-observed client address and consumes a token from the scan bucket. This rate limit is independent of the trial quota.
3. `DeviceAuthenticationFilter` reads `Authorization: Device ...` and checks the current assignment.
4. If the device is licensed, the request bypasses the trial quota and continues.
5. If the device is not licensed, `AnonymousTrialFilter` requires a credential that resolves to a **known device installation**. `PENDING`, `EXPIRED`, and `REVOKED` devices can still use trial; only licensed entitlement bypasses the quota.
6. `DeviceTrialQuotaService` locks the device row inside a PostgreSQL transaction, deletes events **strictly older** than `now - 24h`, counts the remaining events, and inserts a new event if fewer than 3 exist.
7. After the transaction commits successfully, the request enters the controller and analysis begins.

M6 outcomes:

| Situation | HTTP | Code |
| --- | --- | --- |
| Missing, malformed, or unknown device credential | `401` | `TRIAL_DEVICE_REQUIRED` |
| Device has already used 3 admissions in the rolling 24-hour window | `429` | `ANONYMOUS_TRIAL_EXHAUSTED` |
| Database, locking, or persistence failure during admission | `503` | `TRIAL_QUOTA_UNAVAILABLE` |
| Licensed device | Trial quota does not apply | General rate limit still applies |

M6 does not store the raw URL, credential, IP address, query, fragment, or user-agent in `device_trial_scan_event`. The table contains only `event_id`, `device_id`, and `admitted_at`. Old events are swept periodically. This is a safety quota, not fraud detection, device attestation, or a replacement for a WAF or gateway.

### Flow D - Analyze one URL

After a request passes the security and trial gates, `ScanController` calls `ScanService`, which calls `UrlAnalyzer`:

```text
raw input
   │
   ├─ validate length/scheme/authority/credential/port
   v
DefaultUrlNormalizer
   │
   ├─ lowercase scheme/host
   ├─ IDNA UTS #46 -> asciiHost
   ├─ Public Suffix List -> registrableDomain + subdomains
   ├─ IP scope classification
   ├─ build DomainFeatures from asciiHost
   └─ create redacted NormalizedUrl
   v
11 AnalysisRule
   │
   ├─ each rule returns 0 or 1 RuleFinding
   ├─ no I/O, DNS, or fetching
   └─ finding evidence must be safe/redacted
   v
Sort findings: points descending, ruleId ascending
   v
RiskScorer: sum points -> clamp 0..100 -> RiskLevel
   v
ScanResponse: score + risk + normalized fields + findings
```

If a rule throws an exception, the analyzer fails the whole scan with generic `INTERNAL_ERROR`; it does not silently produce a partial score. Logs contain only scan IDs, risk levels, and rule IDs, never the URL.

### Flow E - Trial scan versus licensed scan

| Request type | Analysis | History | `scanId` | Retrieval/AI explanation |
| --- | --- | --- | --- | --- |
| Trial/unlicensed | Yes | No | `null` | No |
| Licensed device | Yes | Yes, owned by `license_id` | UUID | Yes, while retained and when AI is enabled |

Licensed scans create immutable snapshots in `scan_history` and `scan_history_finding`. History is retained for 30 days by default; retrieval checks the cutoff even before scheduled cleanup. All devices granted to the same license share that license's history.

### Flow F - Open a private result

1. The frontend renders an internal `/scans/{scanId}` link only for a response that contains a `scanId`.
2. `GET /api/v1/scans/{scanId}` requires a licensed device.
3. The backend parses the canonical UUID, finds a retained snapshot, and filters by `owner_license_id`.
4. A malformed, missing, expired, ownerless, or cross-license ID returns the same `404 SCAN_NOT_FOUND` response.
5. An administrator token cannot read scans; device credentials and administrator sessions are separate security domains.

### Flow G - AI explanation for a retained scan

1. The user clicks **Explain this result** on their retained scan.
2. The frontend calls `POST /api/v1/scans/{scanId}/explanation` with no request body and no URL.
3. The backend requires a licensed device and reuses the lookup, retention, and owner filtering used by `GET /api/v1/scans/{scanId}`.
4. `ExplanationService` loads `ScanHistory` and creates a `ScanSummary` containing only allowed fields.
5. The backend deterministically builds `riskLevel` and up to three `keyFindings` in the existing finding order.
6. Only `DeepSeekExplanationProvider` may call DeepSeek to create `summary` and `recommendedActions`.
7. The backend strictly validates the output and returns it. AI cannot change the score, risk level, key findings, severity, points, persistence, or access control.

## 5. Rule engine and scoring

Each rule is a separate class and returns at most one `RuleFinding` containing `ruleId`, `severity`, `points`, `title`, `explanation`, and optional evidence. `severity` is a descriptive label; `points` are what contribute to the score.

### 5.1 The 11 current rules

| Rule ID | Detects | Severity | Points | Configuration/description |
| --- | --- | ---: | ---: | --- |
| `MISSING_HTTPS` | Uses `http` instead of `https` | LOW | 5 | Weak signal; HTTP does not prove malicious intent |
| `IP_LITERAL_HOST` | Host is a public IPv4/IPv6 literal | MEDIUM | 15 | There is no domain name for the user to compare |
| `SPECIAL_USE_OR_PRIVATE_HOST` | Private, loopback, link-local, documentation, or other special-use IP | MEDIUM | 15 | Uses the local address table; does not check reachability |
| `EXCESSIVE_URL_LENGTH` | Total raw input exceeds the configured threshold | LOW | 10 | Default `>100`; uses length only and does not echo raw text |
| `EXCESSIVE_SUBDOMAINS` | Subdomain depth exceeds the configured threshold | MEDIUM | 20 | Default `>3`; catches attempts to hide a brand at the beginning of a hostname |
| `SUSPICIOUS_KEYWORDS` | Sensitive words occur in a subdomain label | MEDIUM | 20 | `login`, `verify`, `secure`, `account`, `update`, `confirm`, `signin`, `banking`, `password`, `billing` |
| `BRAND_DOMAIN_MISMATCH` | Brand token occurs in the host but the registrable domain is not the official domain | HIGH | 30 | Small curated registry; no live lookup |
| `BRAND_LOOKALIKE_HOSTNAME` | Host resembles a brand through bounded obfuscation | MEDIUM | 20 | One-character typo, hyphen collapse, or Unicode confusable |
| `PUNYCODE_HOST` | A hostname label starts with `xn--` | MEDIUM | 15 | Punycode can be legitimate; this is only a review signal |
| `ENCODED_CHARACTERS` | Path contains `%XX` encoding | LOW | 10 | Does not decode content and ignores query/fragment |
| `KNOWN_URL_SHORTENER` | Registrable domain belongs to the shortener list | LOW | 10 | A legitimate shortener can hide the final destination |

The rule list and configuration are assembled in `common.config.AnalysisConfig`. Current thresholds and lists can be changed through `backend/src/main/resources/application.yml` instead of being hidden inside domain-class `if` statements.

### 5.2 Deliberately bounded brand detection

The current registry is manually curated local data containing:

| Brand | Official domain |
| --- | --- |
| Vietcombank | `vietcombank.com.vn` |
| Techcombank | `techcombank.com.vn` |
| BIDV | `bidv.com.vn` |
| VietinBank | `vietinbank.vn` |
| Agribank | `agribank.com.vn` |
| ACB | `acb.com.vn` |
| Sacombank | `sacombank.com.vn` |
| MoMo | `momo.vn` |
| Shopee | `shopee.vn` |
| Tiki | `tiki.vn` |

`BRAND_DOMAIN_MISMATCH` matches exact host tokens split on `.` and `-`. If the token occurs on the official registrable domain, the rule does not fire. If a brand is not in the registry, the rule cannot detect it.

`BRAND_LOOKALIKE_HOSTNAME` does not use broad fuzzy matching, embeddings, or a model. It uses three bounded signals:

1. Exactly one edit: insertion, deletion, substitution, or adjacent transposition. The token must be at least five characters long.
2. Hyphen collapse: remove hyphens from a label and compare it exactly with the token.
3. Local Punycode decoding followed by a small explicit, hand-curated Latin/Cyrillic/Greek confusable map. Characters outside the map are ignored instead of guessed.

The two brand rules do not read the path, query, fragment, credentials, DNS, or page content. Evidence names only the brand display name, official domain, and general signal type; it does not echo attacker-controlled hostnames or labels.

### 5.3 Score and risk level

The score is the sum of finding `points`, clamped to `0..100`.

| Score | Risk level | Meaning |
| ---: | --- | --- |
| `0..9` | `LOW` | No strong signal was detected |
| `10..39` | `MODERATE` | Signals deserve review |
| `40..69` | `HIGH` | Multiple signals or one strong signal are present |
| `70..100` | `CRITICAL` | Multiple strong signals occur together |

`LOW` is not a `SAFE` enum and is not a probability of safety. The score is a policy heuristic, has no accuracy claim, and must not be used as a verdict about website content.

## 6. Which AI is used and where

### 6.1 DeepSeek - production AI explanation

**Provider:** DeepSeek.

**Model:** The model is not hard-coded in the application. Deployments must set `DEEPSEEK_MODEL`; the current ADR points to `deepseek-v4-flash` as an approved deployment choice, but the environment always selects the actual model. `DEEPSEEK_API_KEY` is a secret and must not be committed.

**DeepSeek does not:**

- Calculate the score.
- Select `riskLevel`.
- Select key findings.
- Decide license or access control.
- See the raw URL, redacted URL, hostname, path, port, query, fragment, credential, remote address, scan ID, or finding evidence.
- Persist generated explanations.

**DeepSeek receives:**

```text
score
riskLevel
engineVersion
findings[]:
  ruleId
  severity
  points
  title
  generic explanation
```

**DeepSeek is asked to return:**

```json
{
  "summary": "A short, risk-oriented advisory sentence.",
  "recommendedActions": [
    "One or two concrete actions."
  ]
}
```

The provider sends one HTTPS POST to DeepSeek's OpenAI-compatible endpoint with `stream=false`, `max_tokens=300`, `thinking.type=disabled`, JSON mode, and a 20-second timeout. There is no retry and no streaming.

The backend strictly validates the response:

- The top-level object must contain exactly `summary` and `recommendedActions`.
- Keys may not be missing, extra, or duplicated.
- `summary` must be non-blank and at most 300 characters.
- There must be one or two actions; each must be non-blank and at most 200 characters.
- Invalid JSON, an invalid shape, timeout, non-2xx response, or provider failure becomes `503 AI_EXPLANATION_UNAVAILABLE` with a fixed message.

The frontend renders AI output as ordinary React text nodes. It does not parse Markdown/HTML, use `dangerouslySetInnerHTML`, or create anchors. `riskLevel` and `keyFindings` in the response remain backend-deterministic; `data.explanation` is a legacy alias equal to `summary` for v1 compatibility.

The feature is disabled by default:

```text
AI_EXPLANATIONS_ENABLED=false
DEEPSEEK_API_KEY=
DEEPSEEK_MODEL=
```

When enabled, both the API key and model must be configured; missing configuration fails fast during startup.

### 6.2 `ml/` - local machine learning, not the production engine

`ml/` is an offline Python pipeline for feature and baseline-model research. Spring Boot does not call it, it does not replace `DefaultRiskScorer`, and it does not change the API response.

- The feature extractor reads URL text only; it does not fetch, resolve DNS, or follow redirects.
- The sample dataset contains approximately 90 synthetic, hand-authored rows; it must not be used to claim real-world accuracy.
- Available models are `logreg` (default), `random_forest`, and `gradient_boosting`.
- The pipeline uses scikit-learn, StandardScaler, precision/recall/F1, ROC-AUC, and a confusion matrix.
- The default split is `70/15/15`, grouped by canonical host and path so query/fragment/scheme variants do not leak between train and test.
- `predict` returns a predicted risk probability and advisory wording; it does not echo the URL and does not use the word “safe”.

Moving ML into production scoring, or allowing a model to affect risk levels or findings, requires a new ADR and product approval. That is not the current flow.

## 7. Main API

Base path: `/api/v1`.

### Devices and licensing

| Method | Endpoint | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/devices` | Public | Create a device installation; return credential once |
| `GET` | `/api/v1/devices/me` | `Authorization: Device` | Read the current installation state |
| `POST` | `/api/v1/admin/licenses` | Admin session or `X-Admin-Api-Key` | Create a license |
| `GET` | `/api/v1/admin/licenses` | Admin | List licenses |
| `GET` | `/api/v1/admin/licenses/{licenseId}` | Admin | Read a license and its active devices |
| `POST` | `/api/v1/admin/licenses/{licenseId}/devices` | Admin | Grant an activation code |
| `POST` | `/api/v1/admin/licenses/{licenseId}/extend` | Admin | Change expiry |
| `POST` | `/api/v1/admin/licenses/{licenseId}/revoke` | Admin | Revoke the whole license |
| `GET` | `/api/v1/admin/devices/by-code/{activationCode}` | Admin | Look up a device by activation code |
| `GET` | `/api/v1/admin/devices/{deviceId}` | Admin | Look up a device by UUID |
| `POST` | `/api/v1/admin/devices/{deviceId}/revoke` | Admin | Revoke one device assignment |

### Scans and explanations

| Method | Endpoint | Auth | Result |
| --- | --- | --- | --- |
| `POST` | `/api/v1/scans` | Public route; M6 requires a known device credential for trial | Analyze a URL; trial returns `scanId:null`, licensed scans return a UUID |
| `GET` | `/api/v1/scans/{scanId}` | Licensed device | Read a retained snapshot in the same license |
| `POST` | `/api/v1/scans/{scanId}/explanation` | Licensed device | Request DeepSeek advisory when enabled |
| `GET` | `/api/v1/health` | Public | `{ "status":"UP", "service":"linksentry-api" }` |
| `GET` | `/actuator/health` | Public according to deployment | Liveness/readiness with dependency details hidden |

Scan request:

```http
POST /api/v1/scans
Content-Type: application/json
```

```json
{
  "url": "https://login.example.com/account"
}
```

Abbreviated response:

```json
{
  "data": {
    "scanId": null,
    "input": "https://login.example.com/account",
    "normalized": {
      "scheme": "https",
      "host": "login.example.com",
      "asciiHost": "login.example.com",
      "registrableDomain": "example.com",
      "port": null,
      "path": "/account",
      "queryPresent": false,
      "fragmentPresent": false
    },
    "score": 20,
    "riskLevel": "MODERATE",
    "findings": [
      {
        "ruleId": "SUSPICIOUS_KEYWORDS",
        "severity": "MEDIUM",
        "points": 20,
        "title": "Subdomain uses a sensitive-sounding word",
        "explanation": "...",
        "evidence": null
      }
    ],
    "analyzedAt": "2026-08-21T00:00:00Z"
  },
  "meta": {
    "engineVersion": "0.1.0"
  }
}
```

The error envelope contains `code`, `message`, `traceId`, and `timestamp`, and may contain `fieldErrors`. Important codes include:

| HTTP | Code | Meaning |
| ---: | --- | --- |
| `400` | `VALIDATION_ERROR` / `INVALID_URL` | Invalid body or URL |
| `401` | `TRIAL_DEVICE_REQUIRED` | M6 requires a valid trial device credential |
| `401` | `UNAUTHORIZED` / `INVALID_DEVICE_CREDENTIAL` | A protected route has no valid credential |
| `403` | `FORBIDDEN` | Valid credential, but wrong security domain |
| `404` | `SCAN_NOT_FOUND` | Malformed, expired, ownerless, or cross-license scan |
| `409` | `DEVICE_LIMIT_EXCEEDED` | License already has its maximum number of devices |
| `409` | `DEVICE_ALREADY_ASSIGNED` | Device is active under another license |
| `429` | `RATE_LIMITED` | General token bucket is exhausted |
| `429` | `ANONYMOUS_TRIAL_EXHAUSTED` | M6 trial quota is exhausted |
| `503` | `TRIAL_QUOTA_UNAVAILABLE` | M6 cannot check or persist the quota |
| `503` | `AI_EXPLANATION_UNAVAILABLE` | AI is disabled, unconfigured, or the provider failed |
| `500` | `INTERNAL_ERROR` | Unexpected server error with a generic message |

`401` means there is no valid credential; `403` means there is a valid credential but no authority for that route. No endpoint returns a stack trace, exception details, API key, credential, or raw URL.

## 8. Rate limiting and anti-abuse

The general rate limiter uses in-memory token buckets, independently per route and keyed by the server-observed remote address. The M6 trial quota is database-backed per device and is a separate control.

| Bucket | Default capacity | Refill/minute | Applies to |
| --- | ---: | ---: | --- |
| `scan` | 20 | 10 | `POST /api/v1/scans` |
| `scan-lookup` | 30 | 60 | `GET /api/v1/scans/{scanId}` |
| `device` | 5 | 5 | Device bootstrap/status |
| `explanation` | 5 | 2 | DeepSeek explanation; strictest bucket |
| `admin` | 10 | 10 | `/api/v1/admin/**` |
| `admin-auth-login` | 5 | 5 | Administrator browser login only |

Every request matching a bucket consumes a token before Bean Validation or the controller; malformed bodies and invalid URLs still count against the rate limit. `429 RATE_LIMITED` includes `Retry-After`, but does not disclose quota, bucket key, remote address, or `RateLimit-*` headers.

Rate-limit state is not shared between replicas. A multi-instance deployment needs a gateway/WAF or shared/sticky strategy if global quotas are required. M6 trial events are persistent and row-locked in PostgreSQL, but M6 itself is not abuse monitoring.

## 9. Web app and browser extension

### Web app

Main routes:

- `/` - scanner.
- `/methodology` - explanation of registrable domains, signals, and limitations.
- `/license` - activation code and state for the current installation.
- `/scans/{scanId}` - private retained scan.
- `/admin/login` and `/admin` - administrator console separate from the public app.

The frontend uses React, TypeScript, and Vite. It has a shared Axios client, TanStack Query for server state, and Zod for request/response validation. The frontend does not calculate scores, risk levels, or findings; the backend is the source of truth.

### Extension

Build it with:

```bash
cd frontend
npm ci
npm run build:extension
```

The output is `linksentry/` in the repository root. To load it locally:

1. Start PostgreSQL and the backend.
2. Set `VITE_API_BASE_URL` to the local API and update `host_permissions` in the manifest if necessary.
3. Open `chrome://extensions` or `edge://extensions`.
4. Enable Developer mode, choose **Load unpacked**, and select the `linksentry/` directory.
5. Open an `http://` or `https://` tab, click the LinkSentry icon, and choose **Scan this tab**.

The extension inspects the active tab only after the popup opens and reads the raw tab URL again only after the user clicks scan. The URL is not kept in React state, stored, logged, rendered, or opened automatically.

Permissions:

| Permission | Purpose |
| --- | --- |
| `activeTab` | Read the current tab URL after user interaction |
| `storage` | Store the device credential and UI language in `chrome.storage.local` |
| Exact `host_permissions` | Call only the configured API origin; no wildcard and no `<all_urls>` |

The trial extension has core scanning only. A licensed extension can request an AI explanation because the response contains a real `scanId`; the gate uses the server response, not a client-only flag.

## 10. Local setup

### Prerequisites

| Tool | Version |
| --- | --- |
| JDK | **26** |
| Node.js | **20.19+**; Node 24 is recommended |
| npm | 10+ |
| Docker | Compose v2+ |
| PostgreSQL | 17-alpine through `compose.yaml` |

Gradle does not need to be installed globally; use the wrapper in `backend/`.

### Run the stack in separate terminals

**1. Start PostgreSQL**

```bash
docker compose up -d db
docker compose ps
```

**2. Start the backend**

Linux/macOS:

```bash
cd backend
./gradlew bootRun
```

Windows PowerShell:

```powershell
Set-Location backend
.\gradlew.bat bootRun
```

Check the API:

```text
GET http://localhost:8080/api/v1/health
```

Response:

```json
{"status":"UP","service":"linksentry-api"}
```

Local Swagger/OpenAPI:

```text
http://localhost:8080/swagger-ui.html
http://localhost:8080/v3/api-docs
```

**3. Start the frontend**

```bash
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. The frontend calls `http://localhost:8080` by default. If the API origin changes, update `VITE_API_BASE_URL` and the corresponding `CORS_ALLOWED_ORIGINS` value.

### Run everything with Docker Compose

```bash
docker compose --profile backend --profile frontend up -d --build
docker compose ps
```

The frontend container only serves static files; the browser still calls the API origin baked into the bundle. Changing `VITE_API_BASE_URL` or `FRONTEND_PORT` requires a rebuild and matching CORS configuration.

Stop the stack while keeping the database volume:

```bash
docker compose --profile backend --profile frontend down
```

Remove the database volume only when intentionally resetting local data:

```bash
docker compose down -v
```

### Enable the administrator and DeepSeek locally

Spring Boot does not automatically read a `.env` file. Load variables through the IDE, shell, or a secret manager. PowerShell example:

```powershell
$env:ADMIN_BOOTSTRAP_USERNAME = "ops"
$env:ADMIN_BOOTSTRAP_PASSWORD = "change-me-to-a-long-password"
$env:ADMIN_API_KEY = "operator-secret-set-outside-the-repo"

$env:AI_EXPLANATIONS_ENABLED = "true"
$env:DEEPSEEK_API_KEY = "set-real-key-outside-the-repo"
$env:DEEPSEEK_MODEL = "deepseek-v4-flash"

Set-Location backend
.\gradlew.bat bootRun
```

Never commit secrets. Do not place secrets in `frontend/.env*`; every `VITE_` variable is shipped in the browser bundle.

### Quick troubleshooting

**PostgreSQL port `5432` is already in use**

```powershell
$env:POSTGRES_PORT = "55432"
docker compose up -d db
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:55432/linksentry"
Set-Location backend
.\gradlew.bat bootRun
```

**Backend does not start because of a connection error** - PostgreSQL may not be healthy, or `SPRING_DATASOURCE_*` may not match Compose.

**Health card shows Offline** - check the backend port, `VITE_API_BASE_URL`, and the exact origin in `CORS_ALLOWED_ORIGINS`.

**AI returns `503 AI_EXPLANATION_UNAVAILABLE`** - check `AI_EXPLANATIONS_ENABLED`, `DEEPSEEK_API_KEY`, and `DEEPSEEK_MODEL`. A deliberately disabled feature returns the same error code.

## 11. Important configuration

Example files:

| File | Contents |
| --- | --- |
| `.env.example` | Database/Compose local defaults |
| `backend/.env.example` | Datasource, CORS, device licensing, administrator, DeepSeek |
| `frontend/.env.example` | `VITE_API_BASE_URL` |
| `backend/src/main/resources/application.yml` | Typed Spring configuration and default policy |
| `frontend/src/extension/public/manifest.json` | MV3 permissions and exact API host |

| Variable | Default | Meaning |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/linksentry` | PostgreSQL URL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Exact browser origins |
| `ENGINE_VERSION` | `0.1.0` | Version returned in every scan |
| `HISTORY_RETENTION_DAYS` | `30` | Number of days to retain scans |
| `LICENSE_DEFAULT_MAX_DEVICES` | `2` | Default device cap per license |
| `LICENSE_PENDING_DEVICE_RETENTION` | `30d` | Remove devices never granted after this period |
| `ANONYMOUS_TRIAL_ENABLED` | `true` | Enable the M6 trial gate |
| `ANONYMOUS_TRIAL_MAX_SCANS` | `3` | Trial admissions in the rolling window |
| `ANONYMOUS_TRIAL_WINDOW` | `24h` | Rolling quota window |
| `RATE_LIMIT_ENABLED` | `true` | Enable general token buckets |
| `AI_EXPLANATIONS_ENABLED` | `false` | Enable DeepSeek explanations |
| `DEEPSEEK_API_KEY` | empty | Provider secret key |
| `DEEPSEEK_MODEL` | empty | Required model ID when AI is enabled |

Rule policy defaults:

- `linksentry.rules.excessive-url-length.max-length = 100`.
- `linksentry.rules.excessive-subdomains.max-depth = 3`.
- Suspicious keywords and known shortener domains are configured in `application.yml`.
- The brand registry is local curated data; adding or changing a brand is a policy change that requires review.

## 12. Commands and tests

### Frontend

Run from `frontend/`:

| Command | Purpose |
| --- | --- |
| `npm ci` | Install exactly from the lockfile |
| `npm run dev` | Vite development server |
| `npm run format:check` | Check Prettier formatting |
| `npm run lint` | Run ESLint |
| `npm run typecheck` | Run `tsc --noEmit` |
| `npm run test:run` | Run Vitest once |
| `npm run build` | Typecheck and production build |
| `npm run build:extension` | Build the MV3 popup into root `linksentry/` |

### Backend

Run from `backend/`:

| Linux/macOS | Windows PowerShell | Purpose |
| --- | --- | --- |
| `./gradlew bootRun` | `.\gradlew.bat bootRun` | Start the API |
| `./gradlew test --no-daemon` | `.\gradlew.bat test --no-daemon` | Run unit, web, and integration tests |
| `./gradlew build --no-daemon` | `.\gradlew.bat build --no-daemon` | Build, test, and assemble the boot jar |
| `./gradlew bootJar` | `.\gradlew.bat bootJar` | Assemble the executable jar |

The full backend test suite requires Docker because of PostgreSQL Testcontainers. H2 is not evidence for migration, PostgreSQL type, locking, or constraint behavior.

### Local ML

Run tests from the repository root:

```bash
python -m unittest discover -s ml/tests
```

Train, evaluate, and predict:

```bash
cd ml
python -m linksentry_ml train --data data/sample_dataset.csv --model logreg --output-dir artifacts
python -m linksentry_ml evaluate --data data/sample_dataset.csv --model-dir artifacts
python -m linksentry_ml predict "https://example.test/some/path" --model-dir artifacts
```

## 13. Repository layout

```text
.
├── backend/            Spring Boot REST API, analysis engine, Flyway migrations
├── frontend/           React/TypeScript web app and MV3 extension source
├── ml/                 Offline advisory ML toolkit, synthetic dataset, tests
├── docs/               Architecture, API contract, security boundary, ADRs
├── compose.yaml        PostgreSQL and optional backend/frontend containers
├── .github/workflows/  CI for frontend/backend
└── LICENSE             MIT
```

The backend package-by-feature structure and frontend feature-first structure are described in detail in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Main versions

| Component | Version |
| --- | ---: |
| Java toolchain | 26 |
| Gradle wrapper | 9.4.0 |
| Spring Boot | 4.1.0 |
| springdoc-openapi | 3.1.0 |
| PostgreSQL image | 17-alpine |
| Testcontainers | 1.21.3 |
| React / React DOM | 19.2.8 |
| TypeScript | 6.0.3 |
| Vite | 8.2.1 |
| TanStack Query | 5.101.4 |
| Zod | 4.4.3 |
| Vitest | 4.1.10 |
| ICU4J | 78.3 |
| Guava | 33.4.8-jre |
| Bucket4j | 8.19.0 |

The frontend lockfile is committed and CI uses `npm ci`. Major dependency upgrades require reading migration notes and running all relevant gates first.

## 14. Accepted limitations

- Static analysis cannot see page content, reputation, redirect destinations, cloaking, or domain takeover.
- The brand registry is not live and does not cover every brand.
- Valid Punycode does not mean phishing.
- Brand lookalike detection covers only the three bounded techniques above; multi-character typos or confusables outside the map may not be detected.
- Score and risk level are policy heuristics, not probabilities, and have no accuracy claim.
- The general rate limiter is in-memory per instance; multiple replicas do not automatically create a global quota.
- The M6 persistent quota reduces replay after restart and provides concurrency control through PostgreSQL row locking, but it is not device attestation; copied browser storage can still clone a credential until it is revoked.
- An ungranted trial device may be cleaned up after `LICENSE_PENDING_DEVICE_RETENTION`; devices with assignment history are retained.
- AI explanation depends on DeepSeek availability and cost when enabled, but the deterministic scan core does not depend on AI.
- The ML toolkit uses a sample synthetic dataset and has not been productionized.
- There is no global scan-list endpoint; history is always scoped to a license.

## 15. Related documentation

| Document | Contents |
| --- | --- |
| [docs/SECURITY_BOUNDARY.md](docs/SECURITY_BOUNDARY.md) | Text-only URL boundary, privacy, and prohibited behavior |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Layering, package structure, domain rules, and persistence |
| [docs/API_CONTRACT.md](docs/API_CONTRACT.md) | Wire shape, endpoints, error envelope, and contract rules |
| [docs/MANUAL_IMPLEMENTATION_GUIDE.md](docs/MANUAL_IMPLEMENTATION_GUIDE.md) | Original analyzer exercises and decisions |
| [ADR 0001](docs/adr/0001-static-analysis-only.md) | Why the system does not fetch, resolve DNS, or call threat intelligence |
| [ADR 0002](docs/adr/0002-explainable-rule-engine.md) | Why scoring uses a deterministic rule engine |
| [ADR 0003](docs/adr/0003-deterministic-brand-lookalike-detection.md) | Bounded brand lookalike detection |
| [ADR 0005](docs/adr/0005-deepseek-scan-explanation-integration.md) | DeepSeek egress boundary and structured advisory |
| [ADR 0008](docs/adr/0008-device-license-authentication.md) | Device installations and administrator-granted licenses |
| [ADR 0009](docs/adr/0009-idna-uts46-icu4j-decision.md) | ICU4J, UTS #46, and Unicode policy |
| [ADR 0010](docs/adr/0010-persistent-device-scoped-trial-quota.md) | M6 policy, schema, locking, and retention |
| [backend/DEPLOYMENT.md](backend/DEPLOYMENT.md) | Administrator/license deployment and environment variables |
| [ml/README.md](ml/README.md) | Offline ML toolkit and limitations |

## License

[MIT](LICENSE). LinkSentry is a static-analysis and risk-explanation project, not a replacement for a phishing-protection service or professional security review.

# Backend deployment — Render

## Administrator API

The frontend `/admin` console manages licenses through the same HTTP endpoints
under `/api/v1/admin/**` using the separate administrator bearer session. The
`ADMIN_API_KEY` remains an operator-only fallback for `curl` automation and
is never exposed to the frontend or extension. Set it in the Render service's
**Environment** tab if operator automation is required:

| Variable                     | Example                          | Notes                                                                 |
| ----------------------------- | --------------------------------- | ---------------------------------------------------------------------- |
| `ADMIN_API_KEY`               | output of `openssl rand -base64 32` | Secret. Generate one long random value per deployment. Never commit it. |
| `LICENSE_DEFAULT_MAX_DEVICES` | `2`                                | Device cap used when a license is created without an explicit value.   |

An admin request may carry either a valid browser bearer session or the key in
the `X-Admin-Api-Key` header. A missing, blank-configured, or wrong credential
returns the same generic `401 UNAUTHORIZED` envelope every other
unauthenticated route uses; the response never reveals which part of the check
failed. CORS still excludes `X-Admin-Api-Key`, so the browser never uses that
operator header.

### Typical flow: create a license, then grant it a device

1. A user installs the web app or extension. On first use it bootstraps its own
   device installation and shows a **Copy activation code** action — an
   8-character code such as `K7H9-QX3P`. The user sends you that code.
2. Create a license for them:

   ```bash
   curl -X POST "$API_BASE_URL/api/v1/admin/licenses" \
     -H "X-Admin-Api-Key: $ADMIN_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"label": "jane@example.com — annual plan", "expiresAt": "2027-08-20T00:00:00Z", "maxDevices": 2}'
   ```

   `expiresAt` may be `null` for no expiry; `maxDevices` may be omitted to use
   `LICENSE_DEFAULT_MAX_DEVICES`. The response includes the new `licenseId`.

3. Look up the device by the code they sent you, to confirm it before granting
   (optional but recommended — this never requires their device credential,
   which they never have a reason to share):

   ```bash
   curl "$API_BASE_URL/api/v1/admin/devices/by-code/K7H9-QX3P" \
     -H "X-Admin-Api-Key: $ADMIN_API_KEY"
   ```

4. Grant the device to the license:

   ```bash
   curl -X POST "$API_BASE_URL/api/v1/admin/licenses/$LICENSE_ID/devices" \
     -H "X-Admin-Api-Key: $ADMIN_API_KEY" \
     -H "Content-Type: application/json" \
     -d '{"activationCode": "K7H9-QX3P"}'
   ```

   The user's device becomes licensed on its next request — no client action
   needed beyond the periodic status check the web app and extension already
   perform. A second code (their other client — extension or web) is granted
   the same way, up to `maxDevices`.

### Inspecting and managing a license

```bash
# List every license (summary, no device detail)
curl "$API_BASE_URL/api/v1/admin/licenses" -H "X-Admin-Api-Key: $ADMIN_API_KEY"

# Inspect one license and its currently active devices
curl "$API_BASE_URL/api/v1/admin/licenses/$LICENSE_ID" -H "X-Admin-Api-Key: $ADMIN_API_KEY"

# Extend (or shorten) expiry; null means no expiry
curl -X POST "$API_BASE_URL/api/v1/admin/licenses/$LICENSE_ID/extend" \
  -H "X-Admin-Api-Key: $ADMIN_API_KEY" -H "Content-Type: application/json" \
  -d '{"expiresAt": "2028-08-20T00:00:00Z"}'

# Revoke the whole license — every device under it loses access on its next request
curl -X POST "$API_BASE_URL/api/v1/admin/licenses/$LICENSE_ID/revoke" \
  -H "X-Admin-Api-Key: $ADMIN_API_KEY"

# Revoke one device without touching the license or its other devices
curl -X POST "$API_BASE_URL/api/v1/admin/devices/$DEVICE_ID/revoke" \
  -H "X-Admin-Api-Key: $ADMIN_API_KEY"
```

Revocation and expiry take effect server-side on the device's next request —
there is nothing to push to the client. A revoked device can be granted again
later (to the same or a different license) by repeating the grant call above
with its activation code; only the device credential itself never changes, so
this never requires the user to reinstall.

### Residual risk

Device identity is a best-effort entitlement mechanism, not hardware DRM.
Anyone who copies a device's local browser storage (`localStorage` for the web
app, `chrome.storage.local` for the extension) clones its credential and can
use it as that device until it is revoked. Treat a granted device the same way
you would treat a shared password: something you can revoke, not something you
can make un-copyable.

## Admin console authentication

Separate from the Administrator API above. `/admin` in the frontend is a real
browser login for a human administrator, backed by its own `admin_user` and
`admin_session` tables — wholly independent of `ADMIN_API_KEY` and of the
removed end-user account model. There is still no self-service admin sign-up:
exactly one administrator account is created automatically the first time the
backend starts with no admin account yet, from:

| Variable                   | Example                             | Notes                                                                  |
| --------------------------- | ------------------------------------ | ------------------------------------------------------------------------ |
| `ADMIN_BOOTSTRAP_USERNAME`  | `ops`                                | Only read while no admin account exists yet.                            |
| `ADMIN_BOOTSTRAP_PASSWORD`  | output of `openssl rand -base64 24`  | Secret. Never commit it. Only read while no admin account exists yet.   |
| `ADMIN_SESSION_TTL`         | `30m`                                | Optional. Admin login session lifetime; defaults to 30 minutes.         |

Leaving `ADMIN_BOOTSTRAP_USERNAME` or `ADMIN_BOOTSTRAP_PASSWORD` blank does
not fail startup — bootstrap is skipped, the backend still boots and serves
every other route, and `/admin` login fails with the same generic
invalid-credentials response until both are set and the backend restarts with
still no admin account present. Once an administrator exists, these two
variables are never read again — changing or removing them afterward has no
effect, and there is no reset-password flow yet. Only the BCrypt hash of the
password is ever stored; the plaintext value is never logged, returned, or
persisted.

Login returns an opaque bearer token kept only in the browser's
`sessionStorage`, sent as `Authorization: Bearer <token>` to
`/api/v1/admin-auth/**` — a route family entirely separate from
`/api/v1/admin/**` above, so it is never gated by `ADMIN_API_KEY`. Sessions
expire after `ADMIN_SESSION_TTL` and can be revoked early with the
dashboard's **Log out**. Login attempts are independently rate limited per
server-observed address; see `docs/SECURITY_BOUNDARY.md`.

Licenses and device activations are managed through the `/admin` dashboard,
which uses the administrator bearer session directly against the endpoints
above. It never receives or sends `ADMIN_API_KEY`, device credentials, or
other operator secrets. The existing curl flow remains available for
automation.

## Other required environment variables

See `backend/.env.example` for the full list (database, CORS, license
defaults, admin key, admin console authentication, optional AI explanation).
This document only covers the admin API and admin console authentication
above.

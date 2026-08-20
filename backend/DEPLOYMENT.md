# Backend deployment — Render

## Registration email (Resend)

Registration verification email (`POST /api/v2/auth/register` and its resend
route) is delivered through the [Resend](https://resend.com) HTTPS API, not
SMTP. Render's free tier blocks outbound SMTP ports 25, 465, and 587, so no
SMTP configuration works there — the backend calls
`POST https://api.resend.com/emails` over HTTPS (port 443) instead, with a
short explicit timeout, no redirects, and no retries.

Set these two environment variables in the Render service's **Environment**
tab:

| Variable          | Example                                       | Notes                                                         |
| ------------------ | ---------------------------------------------- | -------------------------------------------------------------- |
| `RESEND_API_KEY`   | `re_...`                                       | Secret. Create it at <https://resend.com/api-keys>. Never commit it. |
| `MAIL_FROM`        | `LinkSentry <noreply@verified-domain.com>`     | The `From` header Resend sends with. Its domain **must be verified in Resend** (<https://resend.com/domains>) or every send is rejected. |

Both map directly to Spring configuration keys `linksentry.mail.resend.api-key`
and `linksentry.mail.resend.from` (see `backend/src/main/resources/application.yml`).

### Behavior when unset or misconfigured

- Leaving either variable blank does not fail application startup — the
  backend still boots and serves every other route. Registration itself
  returns the fixed `503 EMAIL_DELIVERY_UNAVAILABLE` response until both are
  set correctly.
- An unverified `MAIL_FROM` domain, an invalid API key, a Resend outage, or a
  network timeout all collapse to the same `503 EMAIL_DELIVERY_UNAVAILABLE`
  response. The backend logs only a safe failure category and, for a non-2xx
  Resend response, its HTTP status code — never the API key, the recipient
  address, the generated code, or Resend's response body.

### Domain verification

`MAIL_FROM`'s domain must be added and verified under **Domains** in the
Resend dashboard before it can send mail. An unverified or missing domain
causes Resend to reject every request with a non-2xx response, which this
backend turns into the same generic `503 EMAIL_DELIVERY_UNAVAILABLE` — check
the Render service logs for `category=PROVIDER_ERROR` if registration email
stops working after a deploy.

## Other required environment variables

See `backend/.env.example` for the full list (database, CORS, session TTL,
OTP TTL/attempts, optional AI explanation). This document only covers the
Resend-specific variables above.

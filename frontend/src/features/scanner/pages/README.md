# `features/scanner/pages`

**Empty by design.** The persisted scan result is a top-level
`src/pages/ScanPage.tsx` route at `/scans/:scanId`; this feature-local folder is
reserved for a future scanner-specific page that needs its own boundary.

The scanner submission flow remains on the home page. Successful scans link to
the persisted result route above.

Requirements that apply to whatever lands here:

- Never render an analysed URL as an anchor, and never call `window.open` with
  one. Render it as text. See
  [SECURITY_BOUNDARY.md](../../../../../docs/SECURITY_BOUNDARY.md).
- Never communicate risk by colour alone.

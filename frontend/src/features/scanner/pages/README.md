# `features/scanner/pages`

**Empty by design.** Reserved for the scan result route added in Exercises 8–9 —
for example a `/scan/:scanId` page once results are persisted.

While the flow stays stateless, the scanner lives on the home page and needs no
route of its own. Do not add one before there is something to link to.

Requirements that apply to whatever lands here:

- Never render an analysed URL as an anchor, and never call `window.open` with
  one. Render it as text. See
  [SECURITY_BOUNDARY.md](../../../../../docs/SECURITY_BOUNDARY.md).
- Never communicate risk by colour alone.

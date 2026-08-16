/** Footer carrying the portfolio and limitations disclaimer. */
export function SiteFooter() {
  return (
    <footer className="border-ink-800 bg-ink-950 mt-16 border-t">
      <div className="text-ink-500 mx-auto max-w-5xl px-4 py-8 text-sm sm:px-6">
        <p className="max-w-3xl">
          LinkSentry is a portfolio project built to demonstrate defensive security engineering. It inspects
          the text of a URL only and cannot determine whether a website is safe. Treat its output as one
          signal among several, never as a verdict.
        </p>
        <p className="mt-3">Static analysis only — no submitted URL is ever visited.</p>
      </div>
    </footer>
  );
}

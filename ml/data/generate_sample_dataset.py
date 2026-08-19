"""Regenerates ml/data/sample_dataset.csv.

This dataset is entirely synthetic and hand-authored -- it exists only to
exercise the pipeline end to end (feature extraction, splitting, training,
evaluation, CLI). It is small, not sampled from real traffic, and must never
be cited as evidence of real-world model accuracy. See ml/README.md
"Dataset and limitations" for the full caveat.

No row references a real organization's actual domain. Run with:
    python ml/data/generate_sample_dataset.py
"""

from __future__ import annotations

import csv
from pathlib import Path

# label 0 = no lexical risk signal identified at labeling time.
# label 1 = lexical risk signals identified at labeling time.
# `group` tags rows that are intentional near-duplicates of one another
# (same host+path, differing only by query/scheme) purely for readability of
# this source file; it is not a dataset column.
ROWS: list[tuple[str, int, str]] = [
    # --- benign-labeled synthetic samples ---
    ("https://www.examplecorp.test/", 0, "root"),
    ("https://www.examplecorp.test/about", 0, "root"),
    ("https://blog.examplecorp.test/posts/2026/roadmap", 0, "blog"),
    ("https://docs.opensourceproject.test/api/v1/reference", 0, "docs"),
    ("https://docs.opensourceproject.test/api/v1/reference?lang=en", 0, "docs-dup"),
    ("https://docs.opensourceproject.test/api/v1/reference?lang=vi", 0, "docs-dup"),
    ("https://shop.northwindtraders.test/cart?id=482", 0, "shop"),
    ("https://shop.northwindtraders.test/cart?id=482&promo=SPRING", 0, "shop-dup"),
    ("https://accounts.examplecorp.test/login", 0, "acct"),
    ("https://accounts.examplecorp.test/login?next=/dashboard", 0, "acct-dup"),
    ("https://news.dailyupdate.test/world/2026/08/story", 0, "news"),
    ("https://mail.examplecorp.test/inbox", 0, "mail"),
    ("http://intranet.examplecorp.test/wiki/onboarding", 0, "intranet"),
    ("https://cdn.staticassets.test/js/app.min.js", 0, "cdn"),
    ("https://api.weatherservice.test/v2/forecast?city=hanoi", 0, "api"),
    ("https://support.examplecorp.test/tickets/new", 0, "support"),
    ("https://community.forumhub.test/t/welcome-thread/1", 0, "forum"),
    ("https://careers.examplecorp.test/openings", 0, "careers"),
    ("https://status.examplecorp.test/", 0, "status"),
    ("https://developer.examplecorp.test/docs/getting-started", 0, "dev"),
    ("https://billing.examplecorp.test/invoices/2026-07", 0, "billing"),
    ("https://accounts.examplecorp.test/password/update", 0, "acct-update"),
    ("https://learn.trainingportal.test/course/python-101", 0, "learn"),
    ("https://wiki.examplecorp.test/Main_Page", 0, "wiki"),
    ("https://images.mediahost.test/gallery/summer", 0, "media"),
    ("https://pay.examplecorp.test/checkout?order=9911", 0, "pay"),
    ("https://sub.a.b.examplecorp.test/deep/nested/path", 0, "deep"),
    ("https://recover.examplecorp.test/account-recovery", 0, "recover"),
    ("https://events.examplecorp.test/2026/summit", 0, "events"),
    ("https://download.opensourceproject.test/releases/1.2.3.tar.gz", 0, "download"),
    ("https://m.examplecorp.test/", 0, "mobile"),
    ("https://en.wikitravel.test/City_Guide", 0, "travel"),
    ("https://192.0.2.10:8443/internal-dashboard", 0, "ip-benign"),
    ("https://gift.examplecorp.test/redeem?code=WELCOME10", 0, "gift-benign"),
    ("https://a.examplecorp.test/", 0, "short"),
    ("https://podcast.examplecorp.test/episodes/42", 0, "podcast"),
    ("https://xn--fr-1ka.test/menu", 0, "punycode-benign"),
    ("https://jobs.examplecorp.test/apply/backend-engineer", 0, "jobs"),
    ("https://forum.examplecorp.test/thread/12345", 0, "forum2"),
    ("https://legal.examplecorp.test/terms-of-service", 0, "legal"),
    ("https://research.examplecorp.test/papers/2026-index", 0, "research"),
    ("https://beta.examplecorp.test/features/preview", 0, "beta"),
    ("https://partners.examplecorp.test/directory", 0, "partners"),
    ("https://feedback.examplecorp.test/survey/2026", 0, "feedback"),
    ("https://assets.examplecorp.test/logo.svg", 0, "assets"),
    ("https://archive.examplecorp.test/2020/index", 0, "archive"),
    # --- risk-signal-labeled synthetic samples ---
    ("http://192.168.10.55/wp-login.php", 1, "ip-1"),
    ("http://192.168.10.55/wp-login.php?redirect=1", 1, "ip-1-dup"),
    ("http://203.0.113.77/verify-account", 1, "ip-2"),
    ("https://secure-login-update.verify-account-now.test/confirm", 1, "keywords-1"),
    ("https://secure-login-update.verify-account-now.test/confirm?step=2", 1, "keywords-1-dup"),
    ("http://account-billing-update.confirm-now.test/signin", 1, "keywords-2"),
    ("https://user:pass1234@banking-secure-login.test/wallet", 1, "creds-1"),
    ("http://admin:letmein@account-recover.test/unlock", 1, "creds-2"),
    ("https://xn--exmplecorp-b2a.test/login", 1, "punycode-1"),
    ("https://xn--exmplecorp-b2a.test/login?ref=email", 1, "punycode-1-dup"),
    ("http://xn--pple-support-4za.test/verify", 1, "punycode-2"),
    ("https://login-secure.account-verify.confirm-billing.urgent-support.test/x", 1, "many-subs"),
    ("http://bonus-gift-limited.free-offer.test/claim?id=1", 1, "spam-1"),
    ("http://bonus-gift-limited.free-offer.test/claim?id=2", 1, "spam-1-dup"),
    ("https://185.220.101.5/invoice/pay-now", 1, "ip-3"),
    ("http://185.220.101.5/invoice/pay-now?amount=500", 1, "ip-3-dup"),
    ("https://secure--account--update.test/%6c%6f%67%69%6e", 1, "encoded-1"),
    ("http://update-your-password-urgent.test/reset%2Fconfirm", 1, "encoded-2"),
    ("https://ap9x2q7z4mf.suspicious-random-host.test/pay", 1, "entropy-1"),
    ("https://kx83jf92mzq1.wallet-support.test/unlock", 1, "entropy-2"),
    ("http://billing-suspend-confirm.recover-support.test/urgent", 1, "keywords-3"),
    ("https://198.51.100.23:4443/secure/login", 1, "ip-port"),
    ("http://verify-billing.account-login-secure.test/confirm?u=1&p=2", 1, "keywords-4"),
    ("https://free-bonus-gift-claim.limited-offer-urgent.test/now", 1, "spam-2"),
    ("http://user:p@ss@login-verify-secure.test/account", 1, "creds-3"),
    ("https://xn--secure-bank-l8a.test/wallet/unlock", 1, "punycode-3"),
    ("http://172.16.5.9/admin/login.php", 1, "ip-4"),
    ("https://recover-account-urgent.confirm-now-secure.test/reset", 1, "keywords-5"),
    ("http://mzq9xk2.entropy-host.suspicious.test/verify", 1, "entropy-3"),
    ("https://login.secure.verify.account.update.confirm.test/x", 1, "many-subs-2"),
    ("http://10.0.0.200/billing/invoice.php?id=1", 1, "ip-5"),
    ("http://10.0.0.200/billing/invoice.php?id=2", 1, "ip-5-dup"),
    ("https://support-urgent-recover.wallet-login.test/unlock?code=99", 1, "keywords-6"),
    ("http://gift-bonus.free-claim-now.limited-time.test/redeem", 1, "spam-3"),
    ("https://xn--login-verify-p3a.account-secure.test/confirm", 1, "punycode-4"),
    ("http://198.18.0.9/secure-signin/index.php", 1, "ip-6"),
    ("https://q7mzx92kf.wallet.entropy-random.test/pay?amount=999", 1, "entropy-4"),
    ("http://user:hunter2@verify-account-billing.test/update", 1, "creds-4"),
    ("https://secure-account-verify.limited-urgent-offer.test/claim", 1, "spam-4"),
    ("http://203.0.113.200/login/wallet/unlock.php", 1, "ip-7"),
    ("https://xn--acc0unt-verify-o8a.test/secure/login", 1, "punycode-5"),
    ("http://recover-billing-urgent.confirm-account.test/reset?t=1", 1, "keywords-7"),
    ("http://recover-billing-urgent.confirm-account.test/reset?t=2", 1, "keywords-7-dup"),
    ("https://kxq9mzp7f2.random-subdomain.entropy-host.test/login", 1, "entropy-5"),
    ("http://172.31.9.14:8080/wallet/secure-login", 1, "ip-port-2"),
]


def main() -> None:
    output_path = Path(__file__).parent / "sample_dataset.csv"
    with output_path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["url", "label", "source", "notes"])
        for url, label, tag in ROWS:
            writer.writerow([url, label, "synthetic-fixture", tag])
    print(f"wrote {len(ROWS)} rows to {output_path}")


if __name__ == "__main__":
    main()

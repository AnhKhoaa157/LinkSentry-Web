"""Static lexical feature extraction for a URL string.

Every function here operates on the URL text alone via :mod:`urllib.parse` and
:mod:`ipaddress`. Nothing in this module opens a socket, resolves a hostname,
or follows a redirect -- the same static-analysis-only boundary the backend
analyzer holds (docs/SECURITY_BOUNDARY.md, ADR 0001). Callers must not log or
persist the raw ``url`` argument; only the numeric feature vector this module
returns is safe to store.
"""

from __future__ import annotations

import ipaddress
import math
import re
from collections import Counter
from urllib.parse import urlsplit

#: Small, hand-curated set of tokens that recur in phishing-style URLs.
#: Deliberately generic (not brand names) and explicitly not exhaustive --
#: mirrors the "curated, not a live feed" limitation documented for the
#: backend's brand registry in docs/SECURITY_BOUNDARY.md.
SUSPICIOUS_TOKENS = frozenset(
    {
        "login",
        "verify",
        "secure",
        "account",
        "update",
        "confirm",
        "signin",
        "password",
        "billing",
        "banking",
        "wallet",
        "unlock",
        "suspend",
        "urgent",
        "limited",
        "bonus",
        "gift",
        "invoice",
        "recover",
        "support",
    }
)

_SPECIAL_CHAR_RE = re.compile(r"[^A-Za-z0-9]")
_PERCENT_ENCODED_RE = re.compile(r"%[0-9A-Fa-f]{2}")
_TOKEN_SPLIT_RE = re.compile(r"[^a-z0-9]+")

#: Ordered feature names. `extract_features` always returns a dict with
#: exactly these keys, in this order -- callers that vectorize (`dataset.py`,
#: `model.py`) rely on this ordering for a stable, explainable feature matrix.
FEATURE_NAMES: tuple[str, ...] = (
    "url_length",
    "hostname_length",
    "path_length",
    "query_length",
    "subdomain_count",
    "label_count",
    "longest_label_length",
    "is_https",
    "num_dots",
    "num_hyphens",
    "num_digits",
    "num_special_chars",
    "special_char_ratio",
    "digit_ratio",
    "num_percent_encoded",
    "num_query_params",
    "has_ip_literal",
    "has_punycode",
    "has_userinfo_credentials",
    "has_at_symbol",
    "has_port",
    "is_default_port",
    "has_double_slash_in_path",
    "suspicious_token_count",
    "hostname_entropy",
)


class UrlFeatureError(ValueError):
    """Raised when a URL string cannot be parsed for feature extraction."""


def shannon_entropy(value: str) -> float:
    """Shannon entropy (bits/char) of `value`; 0.0 for empty input."""
    if not value:
        return 0.0
    counts = Counter(value)
    length = len(value)
    return -sum((count / length) * math.log2(count / length) for count in counts.values())


def is_ip_literal(hostname: str) -> bool:
    """Whether `hostname` (already bracket-stripped by urlsplit) is an IPv4/IPv6 literal."""
    if not hostname:
        return False
    try:
        ipaddress.ip_address(hostname)
        return True
    except ValueError:
        return False


def has_punycode_label(hostname: str) -> bool:
    """Whether any dot-separated label of `hostname` carries an `xn--` Punycode prefix."""
    return any(label.lower().startswith("xn--") for label in hostname.split("."))


def _tokenize(text: str) -> list[str]:
    return [token for token in _TOKEN_SPLIT_RE.split(text.lower()) if token]


def extract_features(url: str) -> dict[str, float]:
    """Extract the static lexical feature vector for `url`.

    Raises `UrlFeatureError` for input that cannot be parsed as `scheme://host...`
    text. Performs no network access; `url` itself must never be logged by the
    caller. Returns a dict with all `FEATURE_NAMES` keys.
    """
    if not isinstance(url, str) or not url.strip():
        raise UrlFeatureError("URL must be a non-empty string")

    try:
        parts = urlsplit(url)
    except ValueError as exc:
        raise UrlFeatureError("URL could not be parsed") from exc

    hostname = (parts.hostname or "").lower()
    if not hostname:
        raise UrlFeatureError("URL has no parseable hostname")

    path = parts.path or ""
    query = parts.query or ""
    labels = [label for label in hostname.split(".") if label]

    try:
        port = parts.port
    except ValueError:
        port = None

    scheme = (parts.scheme or "").lower()
    is_https = scheme == "https"
    default_port = 443 if is_https else 80 if scheme == "http" else None

    special_chars = _SPECIAL_CHAR_RE.findall(url)
    digits = sum(1 for char in url if char.isdigit())
    tokens = _tokenize(hostname + " " + path)

    return {
        "url_length": float(len(url)),
        "hostname_length": float(len(hostname)),
        "path_length": float(len(path)),
        "query_length": float(len(query)),
        "subdomain_count": float(max(len(labels) - 2, 0)),
        "label_count": float(len(labels)),
        "longest_label_length": float(max((len(label) for label in labels), default=0)),
        "is_https": float(is_https),
        "num_dots": float(hostname.count(".")),
        "num_hyphens": float(url.count("-")),
        "num_digits": float(digits),
        "num_special_chars": float(len(special_chars)),
        "special_char_ratio": float(len(special_chars) / len(url)) if url else 0.0,
        "digit_ratio": float(digits / len(url)) if url else 0.0,
        "num_percent_encoded": float(len(_PERCENT_ENCODED_RE.findall(url))),
        "num_query_params": float(len([p for p in query.split("&") if p])) if query else 0.0,
        "has_ip_literal": float(is_ip_literal(hostname)),
        "has_punycode": float(has_punycode_label(hostname)),
        "has_userinfo_credentials": float(bool(parts.username or parts.password)),
        "has_at_symbol": float("@" in url),
        "has_port": float(port is not None),
        "is_default_port": float(port is not None and port == default_port),
        "has_double_slash_in_path": float("//" in path),
        "suspicious_token_count": float(sum(1 for token in tokens if token in SUSPICIOUS_TOKENS)),
        "hostname_entropy": shannon_entropy(hostname),
    }

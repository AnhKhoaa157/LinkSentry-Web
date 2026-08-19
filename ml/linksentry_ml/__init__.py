"""LinkSentry ML: local, advisory URL risk-classification pipeline.

Static-lexical-only, same boundary as the production analyzer (see
docs/SECURITY_BOUNDARY.md and ADR 0001): no network fetch, no DNS resolution,
no redirect following. This package trains and evaluates a baseline model for
local experimentation; it is not wired into the production risk engine.
"""

__version__ = "0.1.0"

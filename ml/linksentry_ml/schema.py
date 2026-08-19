"""Dataset schema and validation for the LinkSentry ML training data.

Dataset format (CSV, UTF-8, header row required)
--------------------------------------------------
Required columns:

- ``url`` (str): the URL text, as a human/analyst would submit it for scanning.
  Parsed only as text -- never fetched, resolved, or followed (same boundary
  as docs/SECURITY_BOUNDARY.md).
- ``label`` (int, 0 or 1): advisory ground-truth risk label.
  - ``0`` = no lexical risk signal was identified for this sample at labeling
    time.
  - ``1`` = lexical risk signals were identified for this sample at labeling
    time.
  This label is independent of, and not derived from, the backend's
  ``RiskLevel`` enum or rule engine -- it is whatever ground truth the
  dataset's curator assigned.

Optional metadata columns (ignored by feature extraction and training,
carried through for traceability only):

- ``source`` (str): where the sample/label came from (e.g. a fixture name).
- ``notes`` (str): free-text annotation.
- ``collected_at`` (str, ISO-8601 date/datetime): when the sample was added.

No other columns are required. Extra columns are tolerated and ignored.
"""

from __future__ import annotations

import pandas as pd

REQUIRED_COLUMNS: tuple[str, ...] = ("url", "label")
OPTIONAL_COLUMNS: tuple[str, ...] = ("source", "notes", "collected_at")

#: Local guard on URL text length. Independent of, and not required to match,
#: the backend's configured maximum input length -- this only bounds what
#: this local pipeline will attempt to featurize/train on.
MAX_URL_LENGTH = 2048

VALID_LABELS = frozenset({0, 1})


class DatasetValidationError(ValueError):
    """Raised when a dataset fails schema validation."""


def validate_dataframe(df: pd.DataFrame) -> None:
    """Validate `df` against the dataset schema.

    Raises `DatasetValidationError` with a description of the first class of
    violation found. Never included in the error message: raw URL values --
    only row counts/positions are reported, per the no-raw-URL-logging rule.
    """
    missing = [col for col in REQUIRED_COLUMNS if col not in df.columns]
    if missing:
        raise DatasetValidationError(f"dataset is missing required column(s): {missing}")

    if len(df) == 0:
        raise DatasetValidationError("dataset has no rows")

    empty_url_rows = df.index[df["url"].isna() | (df["url"].astype(str).str.strip() == "")].tolist()
    if empty_url_rows:
        raise DatasetValidationError(f"{len(empty_url_rows)} row(s) have an empty url")

    too_long_rows = df.index[df["url"].astype(str).str.len() > MAX_URL_LENGTH].tolist()
    if too_long_rows:
        raise DatasetValidationError(
            f"{len(too_long_rows)} row(s) exceed MAX_URL_LENGTH={MAX_URL_LENGTH}"
        )

    try:
        labels = df["label"].astype(int)
    except (ValueError, TypeError) as exc:
        raise DatasetValidationError("label column must be integer-convertible") from exc

    invalid_label_rows = df.index[~labels.isin(VALID_LABELS)].tolist()
    if invalid_label_rows:
        raise DatasetValidationError(
            f"{len(invalid_label_rows)} row(s) have a label outside {sorted(VALID_LABELS)}"
        )

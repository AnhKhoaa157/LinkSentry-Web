"""Dataset loading, validation, feature-matrix construction, and leakage-safe splitting."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

import numpy as np
import pandas as pd
from sklearn.model_selection import GroupShuffleSplit

from .features import FEATURE_NAMES, UrlFeatureError, extract_features
from .schema import validate_dataframe


class DatasetLoadError(ValueError):
    """Raised when a dataset file cannot be loaded or featurized."""


def load_dataset(path: str | Path) -> pd.DataFrame:
    """Load and schema-validate the CSV dataset at `path`."""
    path = Path(path)
    if not path.is_file():
        raise DatasetLoadError(f"dataset file not found: {path}")
    df = pd.read_csv(path, dtype={"label": "Int64"})
    validate_dataframe(df)
    df = df.copy()
    df["label"] = df["label"].astype(int)
    return df


def canonical_key(url: str) -> str:
    """A near-duplicate grouping key for `url`.

    Strips scheme casing, userinfo, port, query, and fragment, and collapses
    a trailing slash, so URLs that differ only by query string, fragment, or
    scheme (http vs https) map to the same key. Used only to keep
    near-duplicates on the same side of a train/val/test split -- never
    persisted or logged.
    """
    parts = urlsplit(url.strip())
    host = (parts.hostname or "").lower()
    path = parts.path or "/"
    if len(path) > 1 and path.endswith("/"):
        path = path[:-1]
    return f"{host}{path}"


@dataclass(frozen=True)
class FeatureMatrix:
    X: np.ndarray
    y: np.ndarray
    feature_names: tuple[str, ...]


def build_feature_matrix(df: pd.DataFrame) -> FeatureMatrix:
    """Featurize every row of `df` (must have `url`/`label` columns).

    Raises `DatasetLoadError` naming the offending row position (never the
    raw URL value) if a URL cannot be parsed.
    """
    rows = []
    for position, url in enumerate(df["url"].astype(str)):
        try:
            features = extract_features(url)
        except UrlFeatureError as exc:
            raise DatasetLoadError(f"row {position}: could not extract features ({exc})") from exc
        rows.append([features[name] for name in FEATURE_NAMES])

    X = np.asarray(rows, dtype=float)
    y = df["label"].to_numpy(dtype=int)
    return FeatureMatrix(X=X, y=y, feature_names=FEATURE_NAMES)


@dataclass(frozen=True)
class DatasetSplit:
    train: pd.DataFrame
    validation: pd.DataFrame
    test: pd.DataFrame


def split_dataset(
    df: pd.DataFrame,
    train_frac: float = 0.7,
    val_frac: float = 0.15,
    test_frac: float = 0.15,
    seed: int = 42,
) -> DatasetSplit:
    """Group-aware train/validation/test split.

    Grouping by `canonical_key` keeps near-duplicate URLs (same host+path,
    differing only by query/fragment/scheme) entirely within one split, so
    the model is never validated or tested on a near-duplicate of a training
    example.
    """
    fractions = (train_frac, val_frac, test_frac)
    if any(f <= 0 for f in fractions) or not np.isclose(sum(fractions), 1.0):
        raise ValueError("train_frac, val_frac, and test_frac must be positive and sum to 1.0")

    groups = df["url"].astype(str).map(canonical_key).to_numpy()
    n_groups = len(set(groups))

    if n_groups < 3:
        raise DatasetLoadError(
            f"dataset has only {n_groups} distinct near-duplicate group(s); "
            "need at least 3 to form train/validation/test splits"
        )

    splitter = GroupShuffleSplit(n_splits=1, train_size=train_frac, random_state=seed)
    train_idx, rest_idx = next(splitter.split(df, groups=groups))

    rest_groups = groups[rest_idx]
    remaining_frac = val_frac + test_frac
    rest_splitter = GroupShuffleSplit(
        n_splits=1, train_size=val_frac / remaining_frac, random_state=seed
    )
    val_pos, test_pos = next(rest_splitter.split(rest_idx, groups=rest_groups))

    val_idx = rest_idx[val_pos]
    test_idx = rest_idx[test_pos]

    return DatasetSplit(
        train=df.iloc[train_idx].reset_index(drop=True),
        validation=df.iloc[val_idx].reset_index(drop=True),
        test=df.iloc[test_idx].reset_index(drop=True),
    )

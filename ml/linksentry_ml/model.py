"""Baseline model training, evaluation, persistence, and single-URL inference.

Advisory only: this module trains/evaluates/predicts locally and is not
wired into the backend risk engine. Predictions must never be described as
"safe" -- see docs/SECURITY_BOUNDARY.md wording rules, applied in
`describe_prediction` below.
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import joblib
import numpy as np
from sklearn.ensemble import GradientBoostingClassifier, RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix, precision_recall_fscore_support, roc_auc_score
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler

from . import __version__
from .dataset import FeatureMatrix
from .features import FEATURE_NAMES, extract_features

MODEL_CHOICES: tuple[str, ...] = ("logreg", "random_forest", "gradient_boosting")

MODEL_FILENAME = "model.joblib"
METADATA_FILENAME = "metadata.json"


def _build_estimator(model_name: str, seed: int):
    if model_name == "logreg":
        return LogisticRegression(max_iter=2000, class_weight="balanced", random_state=seed)
    if model_name == "random_forest":
        return RandomForestClassifier(
            n_estimators=200, class_weight="balanced", random_state=seed, n_jobs=-1
        )
    if model_name == "gradient_boosting":
        return GradientBoostingClassifier(random_state=seed)
    raise ValueError(f"unknown model choice: {model_name!r}; expected one of {MODEL_CHOICES}")


def build_pipeline(model_name: str, seed: int = 42) -> Pipeline:
    """An explainable scaler + estimator pipeline for `model_name`."""
    return Pipeline(
        steps=[
            ("scaler", StandardScaler()),
            ("estimator", _build_estimator(model_name, seed)),
        ]
    )


def train_model(features: FeatureMatrix, model_name: str, seed: int = 42) -> Pipeline:
    """Fit a pipeline on `features`."""
    pipeline = build_pipeline(model_name, seed)
    pipeline.fit(features.X, features.y)
    return pipeline


def evaluate_model(pipeline: Pipeline, features: FeatureMatrix) -> dict[str, Any]:
    """Precision/recall/F1/ROC-AUC and a confusion matrix for `pipeline` on `features`."""
    y_true = features.y
    y_pred = pipeline.predict(features.X)
    y_proba = pipeline.predict_proba(features.X)[:, 1]

    precision, recall, f1, _ = precision_recall_fscore_support(
        y_true, y_pred, average="binary", pos_label=1, zero_division=0
    )

    metrics: dict[str, Any] = {
        "n_samples": int(len(y_true)),
        "precision": float(precision),
        "recall": float(recall),
        "f1": float(f1),
        "confusion_matrix": confusion_matrix(y_true, y_pred, labels=[0, 1]).tolist(),
        "confusion_matrix_labels": ["actual_0", "actual_1"],
    }

    if len(set(y_true.tolist())) < 2:
        metrics["roc_auc"] = None
        metrics["roc_auc_note"] = "undefined: evaluation set contains only one class"
    else:
        metrics["roc_auc"] = float(roc_auc_score(y_true, y_proba))

    return metrics


@dataclass(frozen=True)
class ModelArtifact:
    pipeline: Pipeline
    model_name: str
    feature_names: tuple[str, ...]
    package_version: str
    trained_at: str


def save_artifact(artifact: ModelArtifact, output_dir: str | Path) -> Path:
    """Persist `artifact` (pipeline + metadata) to `output_dir`. Returns the dir."""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    joblib.dump(artifact.pipeline, output_dir / MODEL_FILENAME)
    metadata = {
        "model_name": artifact.model_name,
        "feature_names": list(artifact.feature_names),
        "package_version": artifact.package_version,
        "trained_at": artifact.trained_at,
    }
    (output_dir / METADATA_FILENAME).write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    return output_dir


def load_artifact(model_dir: str | Path) -> ModelArtifact:
    """Load a previously saved `ModelArtifact` from `model_dir`."""
    model_dir = Path(model_dir)
    model_path = model_dir / MODEL_FILENAME
    metadata_path = model_dir / METADATA_FILENAME
    if not model_path.is_file() or not metadata_path.is_file():
        raise FileNotFoundError(f"no trained model found in {model_dir}; run `train` first")

    pipeline = joblib.load(model_path)
    metadata = json.loads(metadata_path.read_text(encoding="utf-8"))
    return ModelArtifact(
        pipeline=pipeline,
        model_name=metadata["model_name"],
        feature_names=tuple(metadata["feature_names"]),
        package_version=metadata["package_version"],
        trained_at=metadata["trained_at"],
    )


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


RISK_PROBABILITY_THRESHOLD = 0.5


def predict_url(artifact: ModelArtifact, url: str) -> dict[str, Any]:
    """Predict a risk probability for `url`. Does not fetch, resolve, or log `url`.

    Returns a dict describing the prediction using risk-oriented wording --
    never "safe" (docs/SECURITY_BOUNDARY.md §4) -- and does not echo the raw
    URL, query string, or credentials back in the result.
    """
    feature_values = extract_features(url)
    if artifact.feature_names != FEATURE_NAMES:
        raise ValueError("model was trained with a different feature set than this package defines")

    x = np.asarray([[feature_values[name] for name in artifact.feature_names]], dtype=float)
    probability = float(artifact.pipeline.predict_proba(x)[0, 1])
    predicted_label = int(probability >= RISK_PROBABILITY_THRESHOLD)

    return {
        "predicted_label": predicted_label,
        "predicted_risk_probability": probability,
        "wording": describe_prediction(probability),
        "model_name": artifact.model_name,
        "package_version": artifact.package_version,
    }


def describe_prediction(probability: float) -> str:
    """Risk-oriented, non-"safe" wording for a predicted probability."""
    if probability >= RISK_PROBABILITY_THRESHOLD:
        return f"predicted risk probability: {probability:.3f} (advisory, not the production score)"
    return f"no signal detected by the advisory model (predicted risk probability: {probability:.3f})"

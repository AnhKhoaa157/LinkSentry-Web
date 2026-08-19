"""CLI for training, evaluating, and predicting with the LinkSentry advisory model.

Run as `python -m linksentry_ml <command>` from the `ml/` directory (see
ml/README.md). Never accepts or produces network I/O; `predict` never prints
the raw URL, query string, or credentials it was given back to the user.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from .dataset import DatasetLoadError, build_feature_matrix, load_dataset, split_dataset
from .model import (
    MODEL_CHOICES,
    ModelArtifact,
    __version__,
    evaluate_model,
    load_artifact,
    now_iso,
    predict_url,
    save_artifact,
    train_model,
)
from .schema import DatasetValidationError

DEFAULT_ARTIFACT_DIR = Path(__file__).resolve().parents[1] / "artifacts"


def _cmd_train(args: argparse.Namespace) -> int:
    df = load_dataset(args.data)
    split = split_dataset(
        df,
        train_frac=args.train_frac,
        val_frac=args.val_frac,
        test_frac=args.test_frac,
        seed=args.seed,
    )

    train_features = build_feature_matrix(split.train)
    val_features = build_feature_matrix(split.validation)
    test_features = build_feature_matrix(split.test)

    pipeline = train_model(train_features, args.model, seed=args.seed)

    artifact = ModelArtifact(
        pipeline=pipeline,
        model_name=args.model,
        feature_names=train_features.feature_names,
        package_version=__version__,
        trained_at=now_iso(),
    )
    output_dir = save_artifact(artifact, args.output_dir)

    report = {
        "model_name": args.model,
        "trained_at": artifact.trained_at,
        "split_sizes": {
            "train": len(split.train),
            "validation": len(split.validation),
            "test": len(split.test),
        },
        "validation_metrics": evaluate_model(pipeline, val_features),
        "test_metrics": evaluate_model(pipeline, test_features),
        "note": "Advisory model only; not integrated into the production risk engine.",
    }
    (output_dir / "metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")

    print(json.dumps(report, indent=2))
    print(f"\nSaved model artifact to: {output_dir}", file=sys.stderr)
    return 0


def _cmd_evaluate(args: argparse.Namespace) -> int:
    artifact = load_artifact(args.model_dir)
    df = load_dataset(args.data)
    features = build_feature_matrix(df)
    metrics = evaluate_model(artifact.pipeline, features)
    metrics["model_name"] = artifact.model_name
    print(json.dumps(metrics, indent=2))
    return 0


def _cmd_predict(args: argparse.Namespace) -> int:
    artifact = load_artifact(args.model_dir)
    result = predict_url(artifact, args.url)
    print(json.dumps(result, indent=2))
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="linksentry_ml",
        description="Local, advisory URL risk-classification pipeline (static lexical features only).",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    train_parser = subparsers.add_parser("train", help="Train a model from a labeled CSV dataset.")
    train_parser.add_argument("--data", required=True, help="Path to the training CSV dataset.")
    train_parser.add_argument("--model", choices=MODEL_CHOICES, default="logreg")
    train_parser.add_argument("--output-dir", default=str(DEFAULT_ARTIFACT_DIR))
    train_parser.add_argument("--seed", type=int, default=42)
    train_parser.add_argument("--train-frac", type=float, default=0.7)
    train_parser.add_argument("--val-frac", type=float, default=0.15)
    train_parser.add_argument("--test-frac", type=float, default=0.15)
    train_parser.set_defaults(func=_cmd_train)

    eval_parser = subparsers.add_parser("evaluate", help="Evaluate a saved model against a CSV dataset.")
    eval_parser.add_argument("--data", required=True, help="Path to the evaluation CSV dataset.")
    eval_parser.add_argument("--model-dir", default=str(DEFAULT_ARTIFACT_DIR))
    eval_parser.set_defaults(func=_cmd_evaluate)

    predict_parser = subparsers.add_parser("predict", help="Predict a risk probability for one URL.")
    predict_parser.add_argument("url", help="URL text to featurize and score (never logged).")
    predict_parser.add_argument("--model-dir", default=str(DEFAULT_ARTIFACT_DIR))
    predict_parser.set_defaults(func=_cmd_predict)

    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except (DatasetLoadError, DatasetValidationError, FileNotFoundError, ValueError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

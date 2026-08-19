import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from linksentry_ml.dataset import build_feature_matrix, load_dataset, split_dataset
from linksentry_ml.model import (
    ModelArtifact,
    __version__,
    describe_prediction,
    evaluate_model,
    load_artifact,
    now_iso,
    predict_url,
    save_artifact,
    train_model,
)

SAMPLE_DATASET = Path(__file__).resolve().parents[1] / "data" / "sample_dataset.csv"


class DescribePredictionWordingTests(unittest.TestCase):
    def test_never_says_safe(self):
        for probability in (0.0, 0.1, 0.49, 0.5, 0.9, 1.0):
            wording = describe_prediction(probability)
            self.assertNotIn("safe", wording.lower())

    def test_low_probability_says_no_signal_detected(self):
        self.assertIn("no signal detected", describe_prediction(0.1))

    def test_high_probability_reports_risk_probability(self):
        self.assertIn("predicted risk probability", describe_prediction(0.9))


class TrainEvaluatePredictRoundTripTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        df = load_dataset(SAMPLE_DATASET)
        cls.split = split_dataset(df, seed=3)
        cls.train_features = build_feature_matrix(cls.split.train)
        cls.val_features = build_feature_matrix(cls.split.validation)
        cls.pipeline = train_model(cls.train_features, "logreg", seed=3)

    def test_evaluate_returns_expected_metric_keys(self):
        metrics = evaluate_model(self.pipeline, self.val_features)
        for key in ("precision", "recall", "f1", "roc_auc", "confusion_matrix", "n_samples"):
            self.assertIn(key, metrics)

    def test_evaluate_metrics_in_valid_ranges(self):
        metrics = evaluate_model(self.pipeline, self.val_features)
        for key in ("precision", "recall", "f1"):
            self.assertGreaterEqual(metrics[key], 0.0)
            self.assertLessEqual(metrics[key], 1.0)
        if metrics["roc_auc"] is not None:
            self.assertGreaterEqual(metrics["roc_auc"], 0.0)
            self.assertLessEqual(metrics["roc_auc"], 1.0)

    def test_save_and_load_artifact_round_trip(self):
        artifact = ModelArtifact(
            pipeline=self.pipeline,
            model_name="logreg",
            feature_names=self.train_features.feature_names,
            package_version=__version__,
            trained_at=now_iso(),
        )
        with tempfile.TemporaryDirectory() as tmp_dir:
            save_artifact(artifact, tmp_dir)
            loaded = load_artifact(tmp_dir)
            self.assertEqual(loaded.model_name, "logreg")
            self.assertEqual(loaded.feature_names, self.train_features.feature_names)

            result = predict_url(loaded, "https://login-verify-secure.example.test/confirm")
            self.assertIn("predicted_risk_probability", result)
            self.assertGreaterEqual(result["predicted_risk_probability"], 0.0)
            self.assertLessEqual(result["predicted_risk_probability"], 1.0)
            self.assertIn(result["predicted_label"], (0, 1))
            self.assertNotIn("safe", result["wording"].lower())

    def test_predict_url_never_echoes_raw_url(self):
        artifact = ModelArtifact(
            pipeline=self.pipeline,
            model_name="logreg",
            feature_names=self.train_features.feature_names,
            package_version=__version__,
            trained_at=now_iso(),
        )
        raw_url = "http://user:hunter2@example.test/very-secret-query?token=abc123"
        result = predict_url(artifact, raw_url)
        for value in result.values():
            self.assertNotIn("hunter2", str(value))
            self.assertNotIn("abc123", str(value))
            self.assertNotIn(raw_url, str(value))


if __name__ == "__main__":
    unittest.main()

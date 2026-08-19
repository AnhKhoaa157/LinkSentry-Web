import contextlib
import io
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from linksentry_ml.cli import main

SAMPLE_DATASET = Path(__file__).resolve().parents[1] / "data" / "sample_dataset.csv"


class CliTrainEvaluatePredictTests(unittest.TestCase):
    def test_train_then_predict_round_trip(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            train_stdout = io.StringIO()
            with contextlib.redirect_stdout(train_stdout):
                exit_code = main(
                    [
                        "train",
                        "--data",
                        str(SAMPLE_DATASET),
                        "--model",
                        "logreg",
                        "--output-dir",
                        tmp_dir,
                        "--seed",
                        "5",
                    ]
                )
            self.assertEqual(exit_code, 0)
            report = json.loads(train_stdout.getvalue())
            self.assertIn("test_metrics", report)
            self.assertTrue((Path(tmp_dir) / "model.joblib").is_file())
            self.assertTrue((Path(tmp_dir) / "metadata.json").is_file())

            predict_stdout = io.StringIO()
            with contextlib.redirect_stdout(predict_stdout):
                exit_code = main(
                    [
                        "predict",
                        "http://login-verify-secure.example.test/confirm",
                        "--model-dir",
                        tmp_dir,
                    ]
                )
            self.assertEqual(exit_code, 0)
            prediction = json.loads(predict_stdout.getvalue())
            self.assertIn("predicted_risk_probability", prediction)
            self.assertNotIn("login-verify-secure", predict_stdout.getvalue())

    def test_predict_without_trained_model_reports_error(self):
        with tempfile.TemporaryDirectory() as tmp_dir:
            stderr = io.StringIO()
            with contextlib.redirect_stderr(stderr):
                exit_code = main(
                    ["predict", "http://example.test/", "--model-dir", tmp_dir]
                )
            self.assertEqual(exit_code, 1)
            self.assertIn("error", stderr.getvalue().lower())


if __name__ == "__main__":
    unittest.main()

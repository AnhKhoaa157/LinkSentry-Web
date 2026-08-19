import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import pandas as pd

from linksentry_ml.dataset import (
    DatasetLoadError,
    build_feature_matrix,
    canonical_key,
    load_dataset,
    split_dataset,
)
from linksentry_ml.schema import DatasetValidationError, validate_dataframe

SAMPLE_DATASET = Path(__file__).resolve().parents[1] / "data" / "sample_dataset.csv"


class ValidateDataframeTests(unittest.TestCase):
    def test_missing_required_column_raises(self):
        df = pd.DataFrame({"url": ["http://example.test/"]})
        with self.assertRaises(DatasetValidationError):
            validate_dataframe(df)

    def test_empty_url_raises(self):
        df = pd.DataFrame({"url": ["", "http://example.test/"], "label": [0, 1]})
        with self.assertRaises(DatasetValidationError):
            validate_dataframe(df)

    def test_invalid_label_raises(self):
        df = pd.DataFrame({"url": ["http://example.test/"], "label": [7]})
        with self.assertRaises(DatasetValidationError):
            validate_dataframe(df)

    def test_valid_dataframe_passes(self):
        df = pd.DataFrame(
            {"url": ["http://example.test/", "http://other.test/"], "label": [0, 1]}
        )
        validate_dataframe(df)  # does not raise


class CanonicalKeyTests(unittest.TestCase):
    def test_query_string_does_not_change_key(self):
        self.assertEqual(
            canonical_key("http://example.test/path?a=1"),
            canonical_key("http://example.test/path?a=2"),
        )

    def test_scheme_does_not_change_key(self):
        self.assertEqual(
            canonical_key("http://example.test/path"),
            canonical_key("https://example.test/path"),
        )

    def test_trailing_slash_does_not_change_key(self):
        self.assertEqual(
            canonical_key("http://example.test/path/"),
            canonical_key("http://example.test/path"),
        )

    def test_different_path_changes_key(self):
        self.assertNotEqual(
            canonical_key("http://example.test/a"),
            canonical_key("http://example.test/b"),
        )


class SplitDatasetLeakageTests(unittest.TestCase):
    def test_near_duplicate_group_stays_on_one_side(self):
        df = pd.DataFrame(
            {
                "url": [
                    "http://example.test/dup?x=1",
                    "http://example.test/dup?x=2",
                    "http://example.test/dup?x=3",
                    "http://one.test/a",
                    "http://two.test/b",
                    "http://three.test/c",
                    "http://four.test/d",
                    "http://five.test/e",
                    "http://six.test/f",
                ],
                "label": [1, 1, 1, 0, 0, 0, 1, 1, 0],
            }
        )
        split = split_dataset(df, train_frac=0.6, val_frac=0.2, test_frac=0.2, seed=1)

        dup_keys = {canonical_key(u) for u in df["url"][:3]}
        for part_name, part in (
            ("train", split.train),
            ("validation", split.validation),
            ("test", split.test),
        ):
            part_dup_rows = part["url"].map(canonical_key).isin(dup_keys).sum()
            self.assertIn(part_dup_rows, (0, 3), f"{part_name} split has a partial duplicate group")

    def test_fractions_must_sum_to_one(self):
        df = pd.DataFrame({"url": ["http://a.test/"] * 5, "label": [0, 1, 0, 1, 0]})
        with self.assertRaises(ValueError):
            split_dataset(df, train_frac=0.5, val_frac=0.5, test_frac=0.5)


class BuildFeatureMatrixTests(unittest.TestCase):
    def test_matrix_shape_matches_dataset(self):
        df = pd.DataFrame(
            {
                "url": ["http://example.test/", "https://other.test/path"],
                "label": [0, 1],
            }
        )
        matrix = build_feature_matrix(df)
        self.assertEqual(matrix.X.shape[0], 2)
        self.assertEqual(matrix.y.tolist(), [0, 1])

    def test_unparseable_url_raises_without_leaking_url_in_message(self):
        df = pd.DataFrame({"url": ["not a url"], "label": [0]})
        with self.assertRaises(DatasetLoadError) as ctx:
            build_feature_matrix(df)
        self.assertNotIn("not a url", str(ctx.exception))


class SampleDatasetTests(unittest.TestCase):
    def test_sample_dataset_loads_and_validates(self):
        df = load_dataset(SAMPLE_DATASET)
        self.assertGreater(len(df), 20)
        self.assertTrue(set(df["label"].unique()).issubset({0, 1}))

    def test_sample_dataset_splits_and_featurizes(self):
        df = load_dataset(SAMPLE_DATASET)
        split = split_dataset(df, seed=7)
        for part in (split.train, split.validation, split.test):
            self.assertGreater(len(part), 0)
            build_feature_matrix(part)


if __name__ == "__main__":
    unittest.main()

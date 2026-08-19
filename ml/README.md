# LinkSentry ML (advisory, local-only)

A local Python pipeline that trains and evaluates a baseline URL
risk-classification model from static lexical features. It is entirely
separate from the backend rule engine: **the backend remains the sole source
of production scores, risk levels, and findings.** This package is for local
training, evaluation, and advisory prediction only, and is not called by the
backend or frontend.

## Boundary

Same static-analysis-only boundary as the rest of LinkSentry
(`docs/SECURITY_BOUNDARY.md`, [ADR 0001](../docs/adr/0001-static-analysis-only.md)):

- No network fetch, DNS resolution, redirect following, or third-party call —
  anywhere in feature extraction, training, evaluation, or prediction.
- No raw URL, query string, or credential is ever logged, printed back, or
  persisted. `predict` prints a probability and wording only, never the URL
  you gave it.
- A prediction is never worded as "safe." Output uses "no signal detected" or
  "predicted risk probability," matching the product-wide wording rule.

## Setup

From the repository root:

```bash
pip install -r ml/requirements.txt
```

All commands below assume your working directory is `ml/` (so `linksentry_ml`
is importable without a separate `pip install -e` step):

```bash
cd ml
```

## Dataset schema

CSV, UTF-8, header row required. See `linksentry_ml/schema.py` for the
authoritative definition.

| Column         | Required | Type          | Meaning                                                                 |
| -------------- | -------- | ------------- | ------------------------------------------------------------------------ |
| `url`          | yes      | string        | URL text, analyzed as text only                                          |
| `label`        | yes      | int, `0`/`1`  | `1` = risk signals identified at labeling time, `0` = none identified    |
| `source`       | no       | string        | Provenance note (e.g. fixture name)                                      |
| `notes`        | no       | string        | Free-text annotation                                                     |
| `collected_at` | no       | ISO-8601 date | When the sample was added                                                |

`label` is an independent, dataset-curator-assigned ground truth. It is
**not** derived from, and has no fixed mapping to, the backend's `RiskLevel`
enum.

## Dataset and limitations

`ml/data/sample_dataset.csv` is a small (~90-row), **entirely synthetic and
hand-authored** fixture (see `ml/data/generate_sample_dataset.py` for exactly
how it was built). It exists only to exercise the pipeline end to end. It is:

- **Not** sampled from real traffic or a real threat feed.
- **Not** representative of real-world URL distributions.
- **Not** evidence of real-world model accuracy, precision, or recall.

Any metric reported by this pipeline against the sample dataset describes
performance on that synthetic fixture only. Train on a real, representative,
properly-licensed dataset before drawing any conclusion about real-world
performance.

## Features

`linksentry_ml/features.py` extracts a fixed-order numeric vector per URL —
see `FEATURE_NAMES` for the full, current list. Categories: length-based
(URL/hostname/path/query, longest label), structural (subdomain count, label
count, query param count), scheme (`is_https`), character-composition
(special-char/digit counts and ratios, percent-encoding count), and
explainable risk indicators (IP-literal host, Punycode label, userinfo
credential marker, `@` presence, non-default port, doubled path slash,
curated suspicious-token count, hostname Shannon entropy).

## CLI

```bash
# Train (writes model.joblib + metadata.json + metrics.json to --output-dir)
python -m linksentry_ml train --data data/sample_dataset.csv --model logreg --output-dir artifacts

# Evaluate a saved model against a (possibly different) labeled CSV
python -m linksentry_ml evaluate --data data/sample_dataset.csv --model-dir artifacts

# Predict a risk probability for one URL (never echoes the URL back)
python -m linksentry_ml predict "https://example.test/some/path" --model-dir artifacts
```

`--model` accepts `logreg` (default), `random_forest`, or `gradient_boosting`.

### Splitting

`train` splits the dataset into train/validation/test (default 70/15/15,
`--train-frac`/`--val-frac`/`--test-frac`) grouped by a canonicalized
host+path key (`linksentry_ml.dataset.canonical_key`), so near-duplicate URLs
— the same page with a different query string, fragment, or scheme — always
land in the same split and can never leak between train and test.

### Metrics

`train` and `evaluate` report precision, recall, F1 (binary, positive class =
`1`), ROC-AUC (when both classes are present in the evaluated set), and a
2x2 confusion matrix, written to stdout and (for `train`) to
`<output-dir>/metrics.json`.

### Artifacts

`train` writes `model.joblib` (the fitted scikit-learn pipeline) and
`metadata.json` (model name, feature names, package version, training
timestamp) to `--output-dir` (default `ml/artifacts/`, gitignored — never
commit a trained model artifact).

## Tests

From the repository root:

```bash
python -m unittest discover -s ml/tests
```

Covers feature extraction, dataset schema validation, near-duplicate-safe
splitting, train/evaluate/predict round-trips, and the CLI.

## Future integration

This model is **not** wired into the production risk engine, and doing so is
out of scope for this package. Per `harness/AGENT_OPERATING_CONTRACT.md` and
[ADR 0001](../docs/adr/0001-static-analysis-only.md), any future step that
would call this model from the backend (or otherwise let it influence a
served score, risk level, or finding) needs its own ADR and explicit product
approval — the same governance already required for any other change to the
scoring path.

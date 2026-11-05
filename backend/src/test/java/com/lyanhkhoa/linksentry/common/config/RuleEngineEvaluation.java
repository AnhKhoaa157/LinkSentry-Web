package com.lyanhkhoa.linksentry.common.config;

import com.lyanhkhoa.linksentry.analysis.domain.AnalysisResult;
import com.lyanhkhoa.linksentry.analysis.domain.AnalysisRule;
import com.lyanhkhoa.linksentry.analysis.domain.InvalidUrlException;
import com.lyanhkhoa.linksentry.analysis.domain.RiskLevel;
import com.lyanhkhoa.linksentry.analysis.domain.RuleFinding;
import com.lyanhkhoa.linksentry.analysis.domain.UrlAnalyzer;
import com.lyanhkhoa.linksentry.analysis.rules.BrandRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * Measures the production rule engine against a labeled URL dataset, offline.
 *
 * <p>Run from {@code backend/}:
 *
 * <pre>{@code ./gradlew evaluateRules --args="../ml/data/sample_dataset.csv"}</pre>
 *
 * <p>The dataset uses the {@code ml/} schema: comma-separated, RFC 4180 quoting, and a header row
 * with at least {@code url} and {@code label} columns, where {@code 1} marks a risky URL and
 * {@code 0} a benign one. Other columns are ignored.
 *
 * <p>The analyzer comes from the real {@code application.yml} through {@link AnalysisConfig}, so the
 * thresholds, word lists, brand registry, and rule order are production's own. Nothing is fetched
 * or resolved, and the report never prints a URL, only counts and rates. Test-only tooling: it is
 * never part of the boot jar.
 */
public final class RuleEngineEvaluation {

    private RuleEngineEvaluation() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("Usage: ./gradlew evaluateRules --args=\"<dataset.csv> [more.csv ...]\"");
            System.exit(2);
        }
        ProductionEngine engine = productionEngine();
        for (String argument : args) {
            Path path = Path.of(argument);
            Report report = evaluate(engine, readDataset(path));
            System.out.println(report.toMarkdown(String.valueOf(path.getFileName())));
        }
    }

    /** The production analyzer and its rule ids in configured order. */
    record ProductionEngine(UrlAnalyzer analyzer, List<String> ruleIds) {}

    /** One dataset row. */
    record LabeledUrl(String url, boolean risky) {}

    static ProductionEngine productionEngine() {
        Binder binder = new Binder(ConfigurationPropertySources.from(applicationYaml()));
        RulesProperties rulesProperties = binder.bind("linksentry.rules", RulesProperties.class)
                .orElseThrow(() -> new IllegalStateException("application.yml has no linksentry.rules"));
        BrandRegistryProperties brandProperties = binder.bind("linksentry.brands", BrandRegistryProperties.class)
                .orElseThrow(() -> new IllegalStateException("application.yml has no linksentry.brands"));

        AnalysisConfig config = new AnalysisConfig();
        BrandRegistry brandRegistry = config.brandRegistry(brandProperties);
        List<AnalysisRule> rules = config.analysisRules(rulesProperties, brandRegistry);
        UrlAnalyzer analyzer = config.urlAnalyzer(config.urlNormalizer(), rules, config.riskScorer());
        return new ProductionEngine(analyzer, rules.stream().map(AnalysisRule::id).toList());
    }

    private static List<PropertySource<?>> applicationYaml() {
        try {
            return new YamlPropertySourceLoader().load("application.yml", new ClassPathResource("application.yml"));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read application.yml from the classpath", exception);
        }
    }

    static List<LabeledUrl> readDataset(Path path) {
        try {
            return parseDataset(Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read dataset " + path, exception);
        }
    }

    /** Parses the dataset. Errors name the row number only, never its URL. */
    static List<LabeledUrl> parseDataset(String content) {
        List<List<String>> records = parseCsv(content.startsWith("﻿") ? content.substring(1) : content);
        if (records.isEmpty()) {
            throw new IllegalArgumentException("Dataset is empty; expected a header row with url and label");
        }
        List<String> header = records.get(0).stream().map(name -> name.strip().toLowerCase(Locale.ROOT)).toList();
        int urlColumn = header.indexOf("url");
        int labelColumn = header.indexOf("label");
        if (urlColumn < 0 || labelColumn < 0) {
            throw new IllegalArgumentException("Dataset header must contain url and label columns");
        }

        List<LabeledUrl> rows = new ArrayList<>();
        for (int index = 1; index < records.size(); index++) {
            List<String> record = records.get(index);
            int rowNumber = index + 1;
            if (record.size() <= Math.max(urlColumn, labelColumn)) {
                throw new IllegalArgumentException("Row " + rowNumber + " is missing the url or label column");
            }
            String label = record.get(labelColumn).strip();
            if (!label.equals("0") && !label.equals("1")) {
                throw new IllegalArgumentException("Row " + rowNumber + " has a label other than 0 or 1");
            }
            rows.add(new LabeledUrl(record.get(urlColumn), label.equals("1")));
        }
        return rows;
    }

    /** Minimal RFC 4180 reader: commas, double-quoted fields, doubled quotes, CRLF or LF, blank lines skipped. */
    static List<List<String>> parseCsv(String content) {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int index = 0; index < content.length(); index++) {
            char character = content.charAt(index);
            if (inQuotes) {
                if (character != '"') {
                    field.append(character);
                } else if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    inQuotes = false;
                }
            } else if (character == '"') {
                inQuotes = true;
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (character == '\n' || character == '\r') {
                if (character == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') {
                    index++;
                }
                fields.add(field.toString());
                field.setLength(0);
                if (!(fields.size() == 1 && fields.get(0).isEmpty())) {
                    records.add(fields);
                }
                fields = new ArrayList<>();
            } else {
                field.append(character);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("Dataset has an unterminated quoted field");
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            records.add(fields);
        }
        return records;
    }

    static Report evaluate(ProductionEngine engine, List<LabeledUrl> rows) {
        List<Outcome> outcomes = new ArrayList<>();
        int rejectedRisky = 0;
        int rejectedBenign = 0;
        for (LabeledUrl row : rows) {
            try {
                AnalysisResult result = engine.analyzer().analyze(row.url());
                Set<String> fired = new LinkedHashSet<>();
                for (RuleFinding finding : result.findings()) {
                    fired.add(finding.ruleId());
                }
                outcomes.add(new Outcome(row.risky(), result.riskLevel(), result.score(), fired));
            } catch (InvalidUrlException rejected) {
                if (row.risky()) {
                    rejectedRisky++;
                } else {
                    rejectedBenign++;
                }
            }
        }
        return new Report(engine.ruleIds(), outcomes, rejectedRisky, rejectedBenign);
    }

    /** The analyzer's verdict for one accepted row. Holds no URL. */
    record Outcome(boolean risky, RiskLevel riskLevel, int score, Set<String> firedRuleIds) {}

    /** Confusion matrix for one "flag at or above this risk level" threshold. */
    record Confusion(RiskLevel threshold, int truePositives, int falsePositives, int trueNegatives, int falseNegatives) {

        double precision() {
            return ratio(truePositives, truePositives + falsePositives);
        }

        double recall() {
            return ratio(truePositives, truePositives + falseNegatives);
        }

        double f1() {
            double precision = precision();
            double recall = recall();
            return Double.isNaN(precision) || Double.isNaN(recall) || precision + recall == 0
                    ? Double.NaN
                    : 2 * precision * recall / (precision + recall);
        }

        double falsePositiveRate() {
            return ratio(falsePositives, falsePositives + trueNegatives);
        }
    }

    record Report(List<String> ruleIds, List<Outcome> outcomes, int rejectedRisky, int rejectedBenign) {

        long count(boolean risky) {
            return outcomes.stream().filter(outcome -> outcome.risky() == risky).count();
        }

        Confusion confusionAt(RiskLevel threshold) {
            int tp = 0;
            int fp = 0;
            int tn = 0;
            int fn = 0;
            for (Outcome outcome : outcomes) {
                boolean flagged = outcome.riskLevel().compareTo(threshold) >= 0;
                if (outcome.risky()) {
                    if (flagged) {
                        tp++;
                    } else {
                        fn++;
                    }
                } else if (flagged) {
                    fp++;
                } else {
                    tn++;
                }
            }
            return new Confusion(threshold, tp, fp, tn, fn);
        }

        /** Rule id to {firings on risky rows, firings on benign rows}, in configured rule order. */
        Map<String, long[]> ruleFirings() {
            Map<String, long[]> firings = new LinkedHashMap<>();
            for (String ruleId : ruleIds) {
                firings.put(ruleId, new long[2]);
            }
            for (Outcome outcome : outcomes) {
                for (String ruleId : outcome.firedRuleIds()) {
                    firings.computeIfAbsent(ruleId, unused -> new long[2])[outcome.risky() ? 0 : 1]++;
                }
            }
            return firings;
        }

        double meanScore(boolean risky) {
            return outcomes.stream()
                    .filter(outcome -> outcome.risky() == risky)
                    .mapToInt(Outcome::score)
                    .average()
                    .orElse(Double.NaN);
        }

        String toMarkdown(String datasetName) {
            long risky = count(true);
            long benign = count(false);
            StringBuilder out = new StringBuilder();
            out.append("## Rule engine evaluation: ").append(datasetName).append("\n\n");
            out.append(String.format(Locale.ROOT, "Rows scored: %d (%d risky, %d benign).", risky + benign, risky,
                    benign));
            out.append(String.format(Locale.ROOT,
                    " Rejected as invalid input and excluded: %d risky, %d benign.%n%n", rejectedRisky,
                    rejectedBenign));
            out.append("| Flag when risk level is at least | TP | FP | TN | FN | Precision | Recall | F1 | False-positive rate |\n");
            out.append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
            for (RiskLevel threshold : List.of(RiskLevel.MODERATE, RiskLevel.HIGH, RiskLevel.CRITICAL)) {
                Confusion confusion = confusionAt(threshold);
                out.append(String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %s | %s | %s | %s |%n",
                        threshold, confusion.truePositives(), confusion.falsePositives(), confusion.trueNegatives(),
                        confusion.falseNegatives(), percent(confusion.precision()), percent(confusion.recall()),
                        percent(confusion.f1()), percent(confusion.falsePositiveRate())));
            }
            out.append(String.format(Locale.ROOT, "%nMean score: risky %s, benign %s.%n%n",
                    decimal(meanScore(true)), decimal(meanScore(false))));
            out.append("| Rule | Fires on risky rows | Fires on benign rows |\n");
            out.append("| --- | ---: | ---: |\n");
            for (Map.Entry<String, long[]> entry : ruleFirings().entrySet()) {
                long[] counts = entry.getValue();
                out.append(String.format(Locale.ROOT, "| %s | %d (%s) | %d (%s) |%n", entry.getKey(), counts[0],
                        percent(ratio(counts[0], risky)), counts[1], percent(ratio(counts[1], benign))));
            }
            return out.toString();
        }
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? Double.NaN : (double) numerator / denominator;
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.1f%%", value * 100);
    }

    private static String decimal(double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, "%.1f", value);
    }
}

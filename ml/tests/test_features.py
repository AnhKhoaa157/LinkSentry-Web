import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from linksentry_ml.features import (
    FEATURE_NAMES,
    UrlFeatureError,
    extract_features,
    has_punycode_label,
    is_ip_literal,
    shannon_entropy,
)


class ShannonEntropyTests(unittest.TestCase):
    def test_empty_string_is_zero(self):
        self.assertEqual(shannon_entropy(""), 0.0)

    def test_single_repeated_character_is_zero(self):
        self.assertEqual(shannon_entropy("aaaa"), 0.0)

    def test_more_diverse_string_has_higher_entropy(self):
        self.assertGreater(shannon_entropy("abcdefgh"), shannon_entropy("aabbccdd"))


class IpLiteralTests(unittest.TestCase):
    def test_ipv4_literal_detected(self):
        self.assertTrue(is_ip_literal("192.168.1.1"))

    def test_ipv6_literal_detected(self):
        self.assertTrue(is_ip_literal("2001:db8::1"))

    def test_hostname_not_ip(self):
        self.assertFalse(is_ip_literal("www.example.test"))

    def test_empty_hostname_not_ip(self):
        self.assertFalse(is_ip_literal(""))


class PunycodeLabelTests(unittest.TestCase):
    def test_detects_punycode_label(self):
        self.assertTrue(has_punycode_label("xn--exmple-cua.test"))

    def test_no_punycode_label(self):
        self.assertFalse(has_punycode_label("www.example.test"))

    def test_case_insensitive(self):
        self.assertTrue(has_punycode_label("XN--EXMPLE-CUA.test"))


class ExtractFeaturesTests(unittest.TestCase):
    def test_rejects_empty_url(self):
        with self.assertRaises(UrlFeatureError):
            extract_features("")

    def test_rejects_unparseable_hostless_url(self):
        with self.assertRaises(UrlFeatureError):
            extract_features("not a url at all")

    def test_returns_all_declared_feature_names(self):
        features = extract_features("https://www.example.test/path?query=1")
        self.assertEqual(set(features.keys()), set(FEATURE_NAMES))

    def test_https_scheme_detected(self):
        https_features = extract_features("https://example.test/")
        http_features = extract_features("http://example.test/")
        self.assertEqual(https_features["is_https"], 1.0)
        self.assertEqual(http_features["is_https"], 0.0)

    def test_ip_literal_feature(self):
        features = extract_features("http://192.168.0.1/login")
        self.assertEqual(features["has_ip_literal"], 1.0)

    def test_punycode_feature(self):
        features = extract_features("https://xn--exmple-cua.test/")
        self.assertEqual(features["has_punycode"], 1.0)

    def test_credential_marker_feature(self):
        features = extract_features("http://user:pass@example.test/")
        self.assertEqual(features["has_userinfo_credentials"], 1.0)
        self.assertEqual(features["has_at_symbol"], 1.0)

    def test_no_credential_marker_for_plain_url(self):
        features = extract_features("http://example.test/")
        self.assertEqual(features["has_userinfo_credentials"], 0.0)

    def test_subdomain_count(self):
        features = extract_features("https://a.b.c.example.test/")
        self.assertEqual(features["subdomain_count"], 3.0)

    def test_suspicious_token_count(self):
        features = extract_features("http://login-verify-secure.example.test/confirm")
        self.assertGreaterEqual(features["suspicious_token_count"], 3.0)

    def test_percent_encoding_counted(self):
        features = extract_features("http://example.test/%6c%6f%67%69%6e")
        self.assertEqual(features["num_percent_encoded"], 5.0)

    def test_query_param_count(self):
        features = extract_features("http://example.test/path?a=1&b=2&c=3")
        self.assertEqual(features["num_query_params"], 3.0)

    def test_double_slash_in_path_detected(self):
        features = extract_features("http://example.test//admin//login")
        self.assertEqual(features["has_double_slash_in_path"], 1.0)

    def test_port_features(self):
        default_port = extract_features("https://example.test:443/")
        custom_port = extract_features("https://example.test:8443/")
        self.assertEqual(default_port["has_port"], 1.0)
        self.assertEqual(default_port["is_default_port"], 1.0)
        self.assertEqual(custom_port["is_default_port"], 0.0)


if __name__ == "__main__":
    unittest.main()

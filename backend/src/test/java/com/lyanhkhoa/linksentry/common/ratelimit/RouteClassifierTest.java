package com.lyanhkhoa.linksentry.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Proves exactly two request shapes are ever charged against a bucket, and that
 * every excluded route (health, actuator, OpenAPI, CORS preflight, wrong method,
 * wrong path depth) falls through unmatched rather than needing an allow-list.
 */
class RouteClassifierTest {

    private final RouteClassifier classifier = new RouteClassifier();

    @Test
    @DisplayName("POST /api/v1/scans classifies as SCAN_CREATE")
    void classifiesScanCreate() {
        assertThat(classifier.classify(request("POST", "/api/v1/scans"))).contains(RateLimitedRoute.SCAN_CREATE);
    }

    @Test
    @DisplayName("GET /api/v1/scans/{scanId} classifies as SCAN_LOOKUP")
    void classifiesScanLookup() {
        assertThat(classifier.classify(request("GET", "/api/v1/scans/2ce16fb9-d52d-4310-8d45-a4e48f31889e")))
                .contains(RateLimitedRoute.SCAN_LOOKUP);
    }

    @Test
    @DisplayName("the bare collection path is not classified as a lookup")
    void bareCollectionPathIsNotLookup() {
        assertThat(classifier.classify(request("GET", "/api/v1/scans"))).isEmpty();
    }

    @Test
    @DisplayName("a path deeper than one segment is not classified as a lookup")
    void deeperPathIsNotLookup() {
        assertThat(classifier.classify(request("GET", "/api/v1/scans/abc/extra"))).isEmpty();
    }

    @Test
    @DisplayName("CORS preflight OPTIONS is never classified, even on a scan path")
    void optionsIsNeverClassified() {
        assertThat(classifier.classify(request("OPTIONS", "/api/v1/scans"))).isEmpty();
        assertThat(classifier.classify(request("OPTIONS", "/api/v1/scans/2ce16fb9-d52d-4310-8d45-a4e48f31889e")))
                .isEmpty();
    }

    @Test
    @DisplayName("health, actuator, and OpenAPI routes are never classified")
    void unrelatedRoutesAreNeverClassified() {
        assertThat(classifier.classify(request("GET", "/api/v1/health"))).isEmpty();
        assertThat(classifier.classify(request("GET", "/actuator/health"))).isEmpty();
        assertThat(classifier.classify(request("GET", "/v3/api-docs"))).isEmpty();
        assertThat(classifier.classify(request("GET", "/swagger-ui.html"))).isEmpty();
    }

    @Test
    @DisplayName("the wrong method on an otherwise-matching path is not classified")
    void wrongMethodIsNotClassified() {
        assertThat(classifier.classify(request("DELETE", "/api/v1/scans/2ce16fb9-d52d-4310-8d45-a4e48f31889e")))
                .isEmpty();
        assertThat(classifier.classify(request("POST", "/api/v1/scans/2ce16fb9-d52d-4310-8d45-a4e48f31889e")))
                .isEmpty();
    }

    private static MockHttpServletRequest request(String method, String path) {
        return new MockHttpServletRequest(method, path);
    }
}

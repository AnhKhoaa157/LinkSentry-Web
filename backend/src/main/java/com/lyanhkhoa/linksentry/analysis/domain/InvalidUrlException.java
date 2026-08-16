package com.lyanhkhoa.linksentry.analysis.domain;

/**
 * Thrown when submitted input cannot be analysed at all — it is blank, too long,
 * malformed, or uses a scheme other than {@code http}/{@code https}.
 *
 * <p>Distinct from "analysed and found risky": this means the input never became a
 * {@link NormalizedUrl}. The scan layer maps it to a {@code 400 INVALID_URL}
 * response (see {@code docs/API_CONTRACT.md}); the domain itself deliberately
 * knows nothing about HTTP status codes.
 *
 * <p><strong>The message must not contain the offending input.</strong> It travels
 * into logs, and a rejected value can still be a URL carrying a token.
 */
public class InvalidUrlException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidUrlException(String message) {
        super(message);
    }

    public InvalidUrlException(String message, Throwable cause) {
        super(message, cause);
    }
}

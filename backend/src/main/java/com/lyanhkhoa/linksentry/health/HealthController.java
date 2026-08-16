package com.lyanhkhoa.linksentry.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Application-level liveness endpoint consumed by the frontend shell.
 *
 * <p>Separate from Actuator on purpose: Actuator's contract is owned by Spring
 * Boot and may change shape between versions, whereas this response is part of
 * our own documented API. It performs no database access, so it stays a true
 * "is the API process answering?" probe.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Service availability")
public class HealthController {

    private static final String SERVICE_NAME = "linksentry-api";

    @GetMapping
    @Operation(summary = "Report that the API is answering requests")
    public HealthResponse health() {
        return new HealthResponse("UP", SERVICE_NAME);
    }
}

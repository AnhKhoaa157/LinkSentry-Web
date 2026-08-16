package com.lyanhkhoa.linksentry.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Describes the API for springdoc-openapi. */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI linkSentryOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LinkSentry API")
                        .version("v1")
                        .description("""
                                Explainable phishing URL analysis.

                                LinkSentry analyses URLs as text only. It never visits a submitted \
                                URL, resolves its DNS, follows its redirects, or downloads its \
                                content. A low risk score is therefore not evidence that a link is \
                                safe.""")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}

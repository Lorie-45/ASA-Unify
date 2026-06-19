package com.asa.asaunify.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ASAUnify API")
                        .description(
                                "Internal Microfinance Request Handler "
                        )
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ASA Microfinance")
                                .email("admin@asamicrofinance.com")
                        )
                )
                // Tells Swagger every endpoint needs a Bearer JWT token
                .addSecurityItem(new SecurityRequirement()
                        .addList("Bearer Authentication")
                )
                .components(new Components()
                        .addSecuritySchemes(
                                "Bearer Authentication",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "Paste your JWT access token here. " +
                                                        "Get it from POST /api/auth/login"
                                        )
                        )
                );
    }
}
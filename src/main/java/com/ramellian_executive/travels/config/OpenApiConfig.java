package com.ramellian_executive.travels.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ramellianOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ramellian Executive Travels API")
                        .version("v1")
                        .description("Authentication and vehicle rental API"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter the JWT returned by /api/v1/auth/login or /register")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}

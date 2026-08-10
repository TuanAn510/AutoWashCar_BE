package com.shinecraft.server.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    OpenAPI washCarServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Wash Car Service API")
                        .version("1.0.0-week1-4")
                        .description("""
                                Backend API for the wash car service project.
                                Scope: customer/admin roles, register/login, booking form, slot availability,
                                survey logs, and temporary hosting support for week 1-4.
                                """)
                        .contact(new Contact().name("Wash Car Service Team"))
                        .license(new License().name("Academic Project")))
                .servers(List.of(new Server().url("http://localhost:8080").description("Local development server")))
                .externalDocs(new ExternalDocumentation()
                        .description("Project report hub")
                        .url("/project-report.html"))
                .components(new Components()
                        .addSecuritySchemes(
                                SECURITY_SCHEME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .tags(List.of(
                        new Tag().name("Health").description("Runtime health check for temporary hosting"),
                        new Tag().name("Auth").description("Customer/admin authentication"),
                        new Tag().name("Catalog").description("Car wash service catalog"),
                        new Tag().name("Vehicles").description("Customer vehicle and license plate APIs"),
                        new Tag().name("Bookings").description("Booking form, availability, and status APIs"),
                        new Tag().name("Loyalty").description("Points, tiers, rewards, and redemptions"),
                        new Tag().name("Promotions").description("Tier-targeted promotions"),
                        new Tag().name("Survey Logs").description("Click/form/page logs for week 1-4 survey"),
                        new Tag().name("Reports").description("Dashboard, CSV export, and project report")));
    }

    @Bean
    GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("01-public-and-customer")
                .pathsToMatch(
                        "/api/health",
                        "/api/auth/**",
                        "/api/catalog/**",
                        "/api/vehicles/**",
                        "/api/bookings/**",
                        "/api/loyalty/**",
                        "/api/rewards/**",
                        "/api/promotions/**",
                        "/api/survey/logs",
                        "/api/project-report")
                .build();
    }

    @Bean
    GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder().group("02-admin").pathsToMatch("/api/admin/**").build();
    }
}

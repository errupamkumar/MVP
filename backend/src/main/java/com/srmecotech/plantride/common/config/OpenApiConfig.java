package com.srmecotech.plantride.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI plantRideOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Plant Ride API")
                        .version("0.1.0")
                        .description("""
                                In-plant cab booking, pooling, fleet compliance and cost-centre billing for LHS.

                                1. Call **POST /api/auth/login** with a demo account (password `Plant@123`):
                                   `LHS-40218` (rider), `DRV-2281` (driver), `admin` (desk + admin), `desk` (desk).
                                2. Click **Authorize** and paste the returned `token`.
                                """)
                        .contact(new Contact().name("SRM ECO TECH").email("info@srmecotech.com")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}

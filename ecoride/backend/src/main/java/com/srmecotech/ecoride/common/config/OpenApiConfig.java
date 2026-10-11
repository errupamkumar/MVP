package com.srmecotech.ecoride.common.config;

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
    public OpenAPI ecoRideOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("EcoRide API")
                        .version("0.1.0")
                        .description("""
                                On-demand ride hailing: rider app, driver app and admin console.

                                Each app signs in on its own endpoint and gets its own token:
                                - Rider: **POST /api/rider/auth/login** (`9876500001` / `Rider@123`)
                                - Driver: **POST /api/driver/auth/login** (`9876600001` / `Driver@123`)
                                - Admin: **POST /api/admin/auth/login** (`admin@ecoride.in` / `Admin@123`)

                                Then click **Authorize** and paste the returned `token`.
                                """)
                        .contact(new Contact().name("SRM ECO TECH").email("info@srmecotech.com")))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}

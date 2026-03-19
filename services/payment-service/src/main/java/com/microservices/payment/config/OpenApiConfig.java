package com.microservices.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI paymentServiceOpenAPI() {
        return new OpenAPI()
            .info(
                new Info()
                    .title("Payment Service API")
                    .version("v1")
                    .description("Payment processing, read, and refund APIs")
                    .contact(new Contact().name("Platform Team").email("platform-team@example.com"))
                    .license(new License().name("Internal Use"))
            );
    }
}

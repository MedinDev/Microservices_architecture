package com.microservices.notification.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI notificationServiceOpenAPI() {
        return new OpenAPI()
            .info(
                new Info()
                    .title("Notification Service API")
                    .version("v1")
                    .description("Notification delivery and preference management APIs")
                    .contact(new Contact().name("Platform Team").email("platform-team@example.com"))
                    .license(new License().name("Internal Use"))
            );
    }
}

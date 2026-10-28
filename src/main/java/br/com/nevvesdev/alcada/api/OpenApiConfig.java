package br.com.nevvesdev.alcada.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI alcadaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("alcada")
                .description("Aprovação e gestão de linhas de crédito com saga orquestrada")
                .version("v1"));
    }
}
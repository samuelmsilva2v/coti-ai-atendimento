package br.com.coti.atendimentos.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("COTI AI Atendimento — api-atendimentos")
                .version("1.0.0")
                .description("API pública de perguntas de alunos. Respostas fundamentadas apenas na base de conhecimento da COTI."));
    }
}

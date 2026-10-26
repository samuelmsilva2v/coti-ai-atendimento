package br.com.coti.conhecimento.infrastructure.config;

import br.com.coti.conhecimento.infrastructure.web.AdminTokenFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfig {

    static final String ESQUEMA_ADMIN = "adminToken";

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("COTI AI Atendimento — api-conhecimento")
                        .version("1.0.0")
                        .description("API administrativa da base de conhecimento. "
                                + "A busca entre serviços é feita exclusivamente pela ferramenta MCP buscar_conhecimento "
                                + "(endpoint /mcp, fora desta documentação). Erros seguem RFC 9457 (application/problem+json)."))
                .components(new Components().addSecuritySchemes(ESQUEMA_ADMIN, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(AdminTokenFilter.HEADER)
                        .description("Token administrativo (ADMIN_API_TOKEN).")));
    }
}

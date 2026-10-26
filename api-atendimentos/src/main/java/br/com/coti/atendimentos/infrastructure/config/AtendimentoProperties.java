package br.com.coti.atendimentos.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Mensagem de fallback (RF10) e URL do MCP Server da api-conhecimento (MCP_CONHECIMENTO_URL). */
@Validated
@ConfigurationProperties("coti.atendimento")
public record AtendimentoProperties(
        @NotBlank String mensagemFallback,
        @NotBlank(message = "defina MCP_CONHECIMENTO_URL")
        @Pattern(regexp = "https?://.+", message = "MCP_CONHECIMENTO_URL deve ser uma URL http(s)")
        String mcpConhecimentoUrl) {
}

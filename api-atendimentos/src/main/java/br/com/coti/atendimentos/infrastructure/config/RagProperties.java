package br.com.coti.atendimentos.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * {@code topK}: limite enviado à ferramenta MCP (1–10, como no contrato). {@code contextoMaxCaracteres}:
 * limite total do contexto RAG (contrato, seção 6.3); precisa ser maior que um chunk (~3.500 caracteres).
 */
@Validated
@ConfigurationProperties("coti.rag")
public record RagProperties(
        @Min(1) @Max(10) int topK,
        @Min(4000) int contextoMaxCaracteres) {
}

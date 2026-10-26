package br.com.coti.conhecimento.infrastructure.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Limiar de similaridade da busca (RAG_SIMILARITY_THRESHOLD; calibrar conforme ADR-014). */
@Validated
@ConfigurationProperties("coti.rag")
public record RagProperties(
        @DecimalMin("0.0") @DecimalMax("1.0") double limiarSimilaridade) {
}

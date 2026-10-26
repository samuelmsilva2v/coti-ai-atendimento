package br.com.coti.conhecimento.infrastructure.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Tamanho e sobreposição dos chunks, em tokens (RF02; CHUNK_SIZE e CHUNK_OVERLAP). */
@Validated
@ConfigurationProperties("coti.chunking")
public record ChunkingProperties(
        @Positive int tamanhoTokens,
        @Min(0) int sobreposicaoTokens) {

    /** RN02.3: a sobreposição precisa ser menor que o chunk, senão a janela não avança. */
    @AssertTrue(message = "CHUNK_OVERLAP deve ser menor que CHUNK_SIZE")
    public boolean isSobreposicaoMenorQueTamanho() {
        return sobreposicaoTokens < tamanhoTokens;
    }
}

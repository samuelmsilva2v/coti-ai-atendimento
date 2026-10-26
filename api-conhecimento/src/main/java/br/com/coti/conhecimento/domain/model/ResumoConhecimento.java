package br.com.coti.conhecimento.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Conhecimento sem o conteúdo integral, para listagens (evita carregar até 100.000 caracteres por item). */
public record ResumoConhecimento(
        UUID id,
        String titulo,
        Categoria categoria,
        String fonte,
        List<String> tags,
        int quantidadeChunks,
        int tamanhoConteudo,
        Instant criadoEm) {

    public ResumoConhecimento {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}

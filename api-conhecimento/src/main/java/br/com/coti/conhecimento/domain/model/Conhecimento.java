package br.com.coti.conhecimento.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Documento integral da base de conhecimento autorizada (RF01). */
public record Conhecimento(
        UUID id,
        String titulo,
        String conteudo,
        Categoria categoria,
        String fonte,
        List<String> tags,
        int quantidadeChunks,
        int tamanhoConteudo,
        Instant criadoEm) {

    public Conhecimento {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(titulo, "titulo");
        Objects.requireNonNull(conteudo, "conteudo");
        Objects.requireNonNull(categoria, "categoria");
        Objects.requireNonNull(fonte, "fonte");
        Objects.requireNonNull(criadoEm, "criadoEm");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}

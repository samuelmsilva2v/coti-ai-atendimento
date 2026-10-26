package br.com.coti.conhecimento.domain.model;

import java.util.UUID;

/** Chunk recuperado pela busca, com a origem para citação (Art. 9). Nunca carrega o vetor (Art. 6). */
public record Trecho(
        UUID chunkId,
        String texto,
        UUID documentoId,
        String titulo,
        String fonte,
        Categoria categoria,
        Double similaridade) {
}

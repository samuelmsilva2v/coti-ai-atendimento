package br.com.coti.conhecimento.mcp.dto;

import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Trecho;

import java.util.UUID;

/** Item de {@code trechos} no resultado da ferramenta (specs/06-api-contracts.md, seção 4.3). */
public record TrechoMcp(
        UUID chunkId,
        String texto,
        UUID documentoId,
        String titulo,
        String fonte,
        Categoria categoria,
        Double similaridade) {

    public static TrechoMcp de(Trecho t) {
        return new TrechoMcp(t.chunkId(), t.texto(), t.documentoId(), t.titulo(), t.fonte(), t.categoria(),
                t.similaridade());
    }
}

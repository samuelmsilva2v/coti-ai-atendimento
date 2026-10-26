package br.com.coti.conhecimento.controller.dto;

import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Trecho;

import java.util.UUID;

/** Mesma estrutura de trecho da ferramenta MCP (specs/06-api-contracts.md, seção 4.3). */
public record TrechoConhecimento(
        UUID chunkId,
        String texto,
        UUID documentoId,
        String titulo,
        String fonte,
        Categoria categoria,
        Double similaridade) {

    public static TrechoConhecimento de(Trecho t) {
        return new TrechoConhecimento(t.chunkId(), t.texto(), t.documentoId(), t.titulo(), t.fonte(), t.categoria(),
                t.similaridade());
    }
}

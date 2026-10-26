package br.com.coti.atendimentos.controller.dto;

import br.com.coti.atendimentos.domain.model.Fonte;

import java.util.UUID;

/** Fonte usada na resposta; o texto do trecho não é devolvido ao aluno (RN09.4). */
public record FonteResposta(
        UUID chunkId,
        UUID documentoId,
        String titulo,
        String fonte,
        String categoria,
        Double similaridade) {

    public static FonteResposta de(Fonte f) {
        return new FonteResposta(f.chunkId(), f.documentoId(), f.titulo(), f.fonte(), f.categoria(), f.similaridade());
    }
}

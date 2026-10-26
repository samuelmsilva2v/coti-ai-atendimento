package br.com.coti.atendimentos.domain.model;

import java.util.UUID;

/** Fonte citada na resposta, construída exclusivamente a partir de uma {@link Evidencia} (RN09.3). */
public record Fonte(
        UUID chunkId,
        UUID documentoId,
        String titulo,
        String fonte,
        String categoria,
        Double similaridade) {

    public static Fonte de(Evidencia e) {
        return new Fonte(e.chunkId(), e.documentoId(), e.titulo(), e.fonte(), e.categoria(), e.similaridade());
    }
}

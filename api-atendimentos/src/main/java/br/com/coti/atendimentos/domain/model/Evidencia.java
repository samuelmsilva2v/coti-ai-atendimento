package br.com.coti.atendimentos.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Trecho recuperado pela ferramenta MCP {@code buscar_conhecimento} (contrato, seção 4.3). O texto é
 * dado não confiável (Art. 10). As fontes da resposta só podem ser derivadas daqui (Art. 9).
 */
public record Evidencia(
        UUID chunkId,
        String texto,
        UUID documentoId,
        String titulo,
        String fonte,
        String categoria,
        Double similaridade) {

    public Evidencia {
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(texto, "texto");
        Objects.requireNonNull(documentoId, "documentoId");
        Objects.requireNonNull(titulo, "titulo");
        Objects.requireNonNull(fonte, "fonte");
        Objects.requireNonNull(categoria, "categoria");
    }
}

package br.com.coti.conhecimento.domain.model;

/**
 * Trecho de um documento. {@code inicio} é o deslocamento (em caracteres) do trecho no conteúdo
 * original, o que permite verificar sobreposição e reconstrução sem perda (RN02.2).
 */
public record Chunk(String texto, int indice, int total, int inicio) {
}

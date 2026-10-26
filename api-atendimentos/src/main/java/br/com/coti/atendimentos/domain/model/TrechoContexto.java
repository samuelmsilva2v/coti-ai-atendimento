package br.com.coti.atendimentos.domain.model;

/**
 * Evidência incluída no contexto, com o id local {@code Tn} visto pelo modelo. O modelo nunca vê
 * {@code chunkId} nem {@code documentoId} (ADR-006); as fontes são derivadas desta evidência.
 */
public record TrechoContexto(String id, Evidencia evidencia) {
}

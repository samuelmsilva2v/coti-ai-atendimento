package br.com.coti.conhecimento.infrastructure.web;

/** Item de {@code erros[]} no formato padronizado de erro (specs/06-api-contracts.md, seção 2). */
public record ErroCampo(String campo, String mensagem) {
}

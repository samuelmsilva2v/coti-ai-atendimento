package br.com.coti.conhecimento.infrastructure.web;

public class AcessoNaoAutorizadoException extends RuntimeException {

    public AcessoNaoAutorizadoException() {
        super("Token administrativo ausente ou inválido");
    }
}

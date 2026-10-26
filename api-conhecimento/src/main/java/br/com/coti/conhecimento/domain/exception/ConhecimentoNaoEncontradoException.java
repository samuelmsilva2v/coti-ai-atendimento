package br.com.coti.conhecimento.domain.exception;

import java.util.UUID;

public class ConhecimentoNaoEncontradoException extends RuntimeException {

    public ConhecimentoNaoEncontradoException(UUID id) {
        super("Conhecimento não encontrado: " + id);
    }
}

package br.com.coti.conhecimento.domain.exception;

/** Argumento de busca fora das regras do contrato (pergunta 1–1000, limite 1–10). */
public class ConsultaInvalidaException extends RuntimeException {

    private final String campo;

    public ConsultaInvalidaException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}

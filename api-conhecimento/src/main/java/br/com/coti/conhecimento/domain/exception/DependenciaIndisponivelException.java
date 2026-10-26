package br.com.coti.conhecimento.domain.exception;

/** Falha de uma dependência externa após as tentativas; vira 503 no contrato (RF10). */
public class DependenciaIndisponivelException extends RuntimeException {

    public enum Dependencia { EMBEDDING, BANCO }

    private final Dependencia dependencia;

    public DependenciaIndisponivelException(Dependencia dependencia, Throwable causa) {
        super("Dependência indisponível: " + dependencia, causa);
        this.dependencia = dependencia;
    }

    public Dependencia dependencia() {
        return dependencia;
    }
}

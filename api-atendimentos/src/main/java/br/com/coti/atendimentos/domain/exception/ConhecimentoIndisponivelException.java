package br.com.coti.atendimentos.domain.exception;

/** A base de conhecimento não pôde ser consultada; vira 503 CONHECIMENTO_INDISPONIVEL (RF10). */
public class ConhecimentoIndisponivelException extends RuntimeException {

    public enum Motivo { TIMEOUT, CONEXAO, ERRO_DA_FERRAMENTA, FERRAMENTA_AUSENTE, RESPOSTA_INVALIDA }

    private final Motivo motivo;

    public ConhecimentoIndisponivelException(Motivo motivo, Throwable causa) {
        super("Conhecimento indisponível: " + motivo, causa);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}

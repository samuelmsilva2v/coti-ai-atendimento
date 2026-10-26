package br.com.coti.atendimentos.domain.exception;

/** O modelo de chat falhou, estourou o tempo ou devolveu saída inválida; vira 503 MODELO_INDISPONIVEL. */
public class ModeloIndisponivelException extends RuntimeException {

    public ModeloIndisponivelException(String motivo, Throwable causa) {
        super("Modelo indisponível: " + motivo, causa);
    }
}

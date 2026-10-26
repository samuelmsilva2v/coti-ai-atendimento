package br.com.coti.conhecimento.domain.model;

import br.com.coti.conhecimento.domain.exception.ConsultaInvalidaException;

/**
 * Consulta de busca semântica já validada (RF04; specs/06-api-contracts.md, seção 4.2). Use
 * {@link #de(String, Integer)}: as regras valem igualmente para a ferramenta MCP e o endpoint de diagnóstico.
 */
public record ConsultaBusca(String pergunta, int limite) {

    public static final int PERGUNTA_MAX = 1000;
    public static final int LIMITE_MIN = 1;
    public static final int LIMITE_MAX = 10;
    public static final int LIMITE_PADRAO = 5;

    public static ConsultaBusca de(String pergunta, Integer limite) {
        if (pergunta == null || pergunta.isBlank()) {
            throw new ConsultaInvalidaException("pergunta", "O argumento 'pergunta' é obrigatório.");
        }
        String normalizada = pergunta.strip();
        if (normalizada.length() > PERGUNTA_MAX) {
            throw new ConsultaInvalidaException("pergunta",
                    "O argumento 'pergunta' deve ter no máximo " + PERGUNTA_MAX + " caracteres.");
        }
        int efetivo = limite == null ? LIMITE_PADRAO : limite;
        if (efetivo < LIMITE_MIN || efetivo > LIMITE_MAX) {
            throw new ConsultaInvalidaException("limite",
                    "O argumento 'limite' deve estar entre " + LIMITE_MIN + " e " + LIMITE_MAX + ".");
        }
        return new ConsultaBusca(normalizada, efetivo);
    }
}

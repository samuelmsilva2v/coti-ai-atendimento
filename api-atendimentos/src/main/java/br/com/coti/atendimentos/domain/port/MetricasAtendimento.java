package br.com.coti.atendimentos.domain.port;

import java.time.Duration;

/** Métricas do atendimento (RNF02.3), sem acoplar a aplicação ao Micrometer. */
public interface MetricasAtendimento {

    /** Tipos de desfecho registrados em {@code coti.atendimento.desfecho{tipo}}. */
    enum Tipo { RESPONDIDA, FALLBACK, CONHECIMENTO_INDISPONIVEL, MODELO_INDISPONIVEL, ERRO_INTERNO }

    void registrarAtendimento(Tipo tipo, Duration duracao);

    void registrarFontesDescartadas(int quantidade);
}

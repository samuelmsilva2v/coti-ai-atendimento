package br.com.coti.atendimentos.domain.model;

/** Resultado de um atendimento, registrado em log e métricas. */
public enum Desfecho {
    /** Resposta fundamentada em fontes. */
    RESPONDIDA,
    /** Mensagem padrão por ausência de evidência (RF10). */
    FALLBACK
}

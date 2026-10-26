package br.com.coti.atendimentos.domain.model;

import java.util.List;
import java.util.Objects;

/** Resultado do atendimento. Em fallback não há fontes (RN09.2). */
public record Resposta(String texto, Desfecho desfecho, List<Fonte> fontes) {

    public Resposta {
        Objects.requireNonNull(texto, "texto");
        Objects.requireNonNull(desfecho, "desfecho");
        fontes = List.copyOf(fontes);
        if (desfecho == Desfecho.FALLBACK && !fontes.isEmpty()) {
            throw new IllegalArgumentException("fallback não tem fontes");
        }
    }

    public static Resposta fallback(String mensagem) {
        return new Resposta(mensagem, Desfecho.FALLBACK, List.of());
    }

    public boolean respondida() {
        return desfecho == Desfecho.RESPONDIDA;
    }

    public boolean isFallback() {
        return desfecho == Desfecho.FALLBACK;
    }
}

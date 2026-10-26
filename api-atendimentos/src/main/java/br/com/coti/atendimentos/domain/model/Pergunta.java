package br.com.coti.atendimentos.domain.model;

import java.util.Objects;

/** Pergunta do aluno, já validada na fronteira REST (RF06). Nunca vai para o log (Art. 13). */
public record Pergunta(String texto) {

    public Pergunta {
        Objects.requireNonNull(texto, "texto");
    }

    @Override
    public String toString() {
        return "Pergunta[tamanho=" + texto.length() + "]";
    }
}

package br.com.coti.conhecimento.domain.model;

import java.util.List;

/** Dados de entrada do cadastro, já validados na fronteira REST. */
public record NovoConhecimento(String titulo, String conteudo, Categoria categoria, String fonte, List<String> tags) {

    public NovoConhecimento {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}

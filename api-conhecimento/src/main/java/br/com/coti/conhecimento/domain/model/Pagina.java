package br.com.coti.conhecimento.domain.model;

import java.util.List;

public record Pagina<T>(List<T> itens, int pagina, int tamanho, long totalItens) {

    public Pagina {
        itens = List.copyOf(itens);
    }

    public int totalPaginas() {
        return (int) ((totalItens + tamanho - 1) / tamanho);
    }
}

package br.com.coti.conhecimento.domain.model;

import java.util.List;

/** Trechos com similaridade ≥ limiar; lista vazia significa ausência de evidência, não erro (RN04.2). */
public record ResultadoBusca(List<Trecho> trechos, double limiarSimilaridade) {

    public ResultadoBusca {
        trechos = List.copyOf(trechos);
    }
}

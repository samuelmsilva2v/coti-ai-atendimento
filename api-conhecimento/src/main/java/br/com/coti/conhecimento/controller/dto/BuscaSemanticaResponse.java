package br.com.coti.conhecimento.controller.dto;

import br.com.coti.conhecimento.domain.model.ResultadoBusca;

import java.util.List;

public record BuscaSemanticaResponse(List<TrechoConhecimento> trechos, int total, double limiarSimilaridade) {

    public static BuscaSemanticaResponse de(ResultadoBusca resultado) {
        List<TrechoConhecimento> trechos = resultado.trechos().stream().map(TrechoConhecimento::de).toList();
        return new BuscaSemanticaResponse(trechos, trechos.size(), resultado.limiarSimilaridade());
    }
}

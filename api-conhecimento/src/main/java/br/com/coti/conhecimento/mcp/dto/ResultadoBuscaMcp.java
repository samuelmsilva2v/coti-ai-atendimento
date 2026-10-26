package br.com.coti.conhecimento.mcp.dto;

import br.com.coti.conhecimento.domain.model.ResultadoBusca;

import java.util.List;

/** {@code structuredContent} de sucesso da ferramenta (outputSchema do contrato, seção 4.2). */
public record ResultadoBuscaMcp(List<TrechoMcp> trechos, int total) {

    public static ResultadoBuscaMcp de(ResultadoBusca resultado) {
        List<TrechoMcp> trechos = resultado.trechos().stream().map(TrechoMcp::de).toList();
        return new ResultadoBuscaMcp(trechos, trechos.size());
    }
}

package br.com.coti.atendimentos.domain.port;

import br.com.coti.atendimentos.domain.model.ContextoRag;
import br.com.coti.atendimentos.domain.model.SaidaModelo;

/** Geração da resposta pelo modelo de linguagem, só com o contexto recuperado (Art. 9). */
public interface GeradorDeResposta {

    /**
     * @param contexto contexto não vazio (contexto vazio é fallback e nunca chega aqui)
     * @throws br.com.coti.atendimentos.domain.exception.ModeloIndisponivelException em falha, timeout ou
     *         saída estruturada inválida
     */
    SaidaModelo gerar(ContextoRag contexto);
}

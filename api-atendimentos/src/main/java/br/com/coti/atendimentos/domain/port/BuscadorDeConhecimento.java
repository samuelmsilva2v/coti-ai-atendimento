package br.com.coti.atendimentos.domain.port;

import br.com.coti.atendimentos.domain.model.Evidencia;
import br.com.coti.atendimentos.domain.model.Pergunta;

import java.util.List;

/** Acesso ao conhecimento, exclusivamente via a ferramenta MCP da api-conhecimento (Art. 4). */
public interface BuscadorDeConhecimento {

    /**
     * @return evidências com similaridade acima do limiar do servidor; lista vazia = sem evidência
     * @throws br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException em qualquer
     *         falha (timeout, conexão, erro da ferramenta, resposta inválida). Falha nunca vira lista vazia (RN07.4).
     */
    List<Evidencia> buscar(Pergunta pergunta, int limite);
}

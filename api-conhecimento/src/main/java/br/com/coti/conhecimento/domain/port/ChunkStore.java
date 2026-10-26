package br.com.coti.conhecimento.domain.port;

import br.com.coti.conhecimento.domain.model.Chunk;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.ConsultaBusca;
import br.com.coti.conhecimento.domain.model.ResultadoBusca;

import java.util.List;

/** Armazenamento vetorial dos chunks; gerar embeddings é responsabilidade da implementação (Art. 6). */
public interface ChunkStore {

    /**
     * Gera os embeddings e grava os chunks com os metadados do documento.
     *
     * @throws br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException se o provedor de
     *         embeddings falhar
     */
    void adicionar(Conhecimento conhecimento, List<Chunk> chunks);

    /**
     * Busca por similaridade de cosseno com o mesmo EmbeddingModel da ingestão (CA03.3), aplicando o
     * limiar configurado. Nunca completa a lista com trechos abaixo do limiar (RN04.1).
     *
     * @throws br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException se o provedor de
     *         embeddings ou o banco falharem
     */
    ResultadoBusca buscar(ConsultaBusca consulta);
}

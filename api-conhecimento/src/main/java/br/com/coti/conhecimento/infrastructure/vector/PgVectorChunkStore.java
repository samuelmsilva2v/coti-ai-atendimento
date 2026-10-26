package br.com.coti.conhecimento.infrastructure.vector;

import br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException;
import br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException.Dependencia;
import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Chunk;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.ConsultaBusca;
import br.com.coti.conhecimento.domain.model.ResultadoBusca;
import br.com.coti.conhecimento.domain.model.Trecho;
import br.com.coti.conhecimento.domain.port.ChunkStore;
import br.com.coti.conhecimento.infrastructure.config.RagProperties;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Grava e busca chunks no PgVectorStore; o embedding é gerado pelo próprio store com o EmbeddingModel. */
@Component
@EnableConfigurationProperties(RagProperties.class)
class PgVectorChunkStore implements ChunkStore {

    static final String DOCUMENTO_ID = "documentoId";
    static final String TITULO = "titulo";
    static final String FONTE = "fonte";
    static final String CATEGORIA = "categoria";
    static final String TAGS = "tags";
    static final String CHUNK_INDEX = "chunkIndex";
    static final String TOTAL_CHUNKS = "totalChunks";
    static final String CRIADO_EM = "criadoEm";

    private final VectorStore vectorStore;
    private final double limiar;

    PgVectorChunkStore(VectorStore vectorStore, RagProperties propriedades) {
        this.vectorStore = vectorStore;
        this.limiar = propriedades.limiarSimilaridade();
    }

    @Override
    public void adicionar(Conhecimento conhecimento, List<Chunk> chunks) {
        List<Document> documentos = chunks.stream().map(c -> documento(conhecimento, c)).toList();
        try {
            vectorStore.add(documentos);
        } catch (DataAccessException e) {
            throw e; // falha de banco: tratada como BANCO_INDISPONIVEL pelo handler
        } catch (RuntimeException e) {
            throw new DependenciaIndisponivelException(Dependencia.EMBEDDING, e);
        }
    }

    @Override
    public ResultadoBusca buscar(ConsultaBusca consulta) {
        List<Document> documentos;
        try {
            documentos = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(consulta.pergunta())
                    .topK(consulta.limite())
                    .similarityThreshold(limiar)
                    .build());
        } catch (DataAccessException e) {
            throw new DependenciaIndisponivelException(Dependencia.BANCO, e);
        } catch (RuntimeException e) {
            throw new DependenciaIndisponivelException(Dependencia.EMBEDDING, e);
        }
        return new ResultadoBusca(documentos.stream().map(PgVectorChunkStore::trecho).toList(), limiar);
    }

    private static Document documento(Conhecimento conhecimento, Chunk chunk) {
        Map<String, Object> metadados = Map.of(
                DOCUMENTO_ID, conhecimento.id().toString(),
                TITULO, conhecimento.titulo(),
                FONTE, conhecimento.fonte(),
                CATEGORIA, conhecimento.categoria().name(),
                TAGS, conhecimento.tags(),
                CHUNK_INDEX, chunk.indice(),
                TOTAL_CHUNKS, chunk.total(),
                CRIADO_EM, conhecimento.criadoEm().toString());
        return Document.builder().id(UUID.randomUUID().toString()).text(chunk.texto()).metadata(metadados).build();
    }

    private static Trecho trecho(Document d) {
        Map<String, Object> m = d.getMetadata();
        return new Trecho(
                UUID.fromString(d.getId()),
                d.getText(),
                UUID.fromString((String) m.get(DOCUMENTO_ID)),
                (String) m.get(TITULO),
                (String) m.get(FONTE),
                Categoria.valueOf((String) m.get(CATEGORIA)),
                d.getScore() == null ? null : Math.round(d.getScore() * 10_000) / 10_000.0);
    }
}

package br.com.coti.conhecimento.infrastructure.vector;

import br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException;
import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Chunk;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.port.ChunkStore;
import br.com.coti.conhecimento.support.FakeEmbeddingModel;
import br.com.coti.conhecimento.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class PgVectorChunkStoreIT {

    @Autowired
    private ChunkStore chunkStore;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FakeEmbeddingModel embeddings;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void limpar() {
        jdbc.sql("TRUNCATE vector_store").update();
        embeddings.reiniciar();
    }

    /** O contexto (e o FakeEmbeddingModel) é compartilhado: a falha simulada não pode vazar para outras classes. */
    @AfterEach
    void restaurar() {
        embeddings.reiniciar();
    }

    @Test
    void gravaUmRegistroPorChunkComVetorEMetadados() {   // CA02.3 e CA03.1
        var conhecimento = conhecimento();
        var chunks = List.of(new Chunk("Primeiro trecho.", 0, 2, 0), new Chunk("Segundo trecho.", 1, 2, 10));

        chunkStore.adicionar(conhecimento, chunks);

        List<Map<String, Object>> linhas = jdbc.sql("""
                SELECT content, metadata::text AS metadata, vector_dims(embedding) AS dims
                FROM vector_store ORDER BY (metadata->>'chunkIndex')::int
                """).query().listOfRows();
        assertThat(linhas).hasSize(2);
        assertThat(linhas).extracting(l -> l.get("content")).containsExactly("Primeiro trecho.", "Segundo trecho.");
        assertThat(linhas).allSatisfy(l -> assertThat(l.get("dims")).isEqualTo(1536));

        JsonNode meta = json.readTree((String) linhas.get(1).get("metadata"));
        assertThat(meta.get("documentoId").asString()).isEqualTo(conhecimento.id().toString());
        assertThat(meta.get("titulo").asString()).isEqualTo("Curso Java Web Developer");
        assertThat(meta.get("fonte").asString()).isEqualTo("https://www.cotiinformatica.com.br/java");
        assertThat(meta.get("categoria").asString()).isEqualTo("CURSO");
        assertThat(meta.get("tags").get(0).asString()).isEqualTo("java");
        assertThat(meta.get("chunkIndex").asInt()).isEqualTo(1);
        assertThat(meta.get("totalChunks").asInt()).isEqualTo(2);
        assertThat(meta.get("criadoEm").asString()).isEqualTo("2026-10-25T18:30:00Z");
    }

    @Test
    void falhaDoProvedorViraDependenciaIndisponivelSemGravar() {
        embeddings.falharAPartirDaChamada(1);

        assertThatThrownBy(() -> chunkStore.adicionar(conhecimento(), List.of(new Chunk("x", 0, 1, 0))))
                .isInstanceOf(DependenciaIndisponivelException.class)
                .extracting(e -> ((DependenciaIndisponivelException) e).dependencia())
                .isEqualTo(DependenciaIndisponivelException.Dependencia.EMBEDDING);
        assertThat(jdbc.sql("SELECT count(*) FROM vector_store").query(Long.class).single()).isZero();
    }

    private static Conhecimento conhecimento() {
        return new Conhecimento(UUID.randomUUID(), "Curso Java Web Developer", "Primeiro trecho. Segundo trecho.",
                Categoria.CURSO, "https://www.cotiinformatica.com.br/java", List.of("java"), 2, 32,
                Instant.parse("2026-10-25T18:30:00Z"));
    }
}

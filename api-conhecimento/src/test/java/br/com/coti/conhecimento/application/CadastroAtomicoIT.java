package br.com.coti.conhecimento.application;

import br.com.coti.conhecimento.infrastructure.web.AdminTokenFilter;
import br.com.coti.conhecimento.support.FakeEmbeddingModel;
import br.com.coti.conhecimento.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Ingestão ponta a ponta: REST → chunking → embeddings (falsos) → pgvector, de forma atômica. */
@IntegrationTest
class CadastroAtomicoIT {

    private static final String TOKEN = "token-de-teste-0123456789";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FakeEmbeddingModel embeddings;

    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeEach
    void limpar() {
        jdbc.sql("TRUNCATE conhecimento, vector_store").update();
        embeddings.reiniciar();
    }

    @AfterEach
    void restaurar() {
        embeddings.reiniciar();
    }

    @Test
    void cadastroGravaDocumentoEChunksComEmbeddings() {   // CA01.1 e CA03.1
        var resultado = cadastrar(textoLongo(3_000));

        assertThat(resultado).hasStatus(201);
        String id = assertThat(resultado).bodyJson().extractingPath("$.id").asString().actual();
        int quantidade = ((Number) assertThat(resultado).bodyJson().extractingPath("$.quantidadeChunks")
                .asNumber().actual()).intValue();
        assertThat(quantidade).isGreaterThanOrEqualTo(2);
        assertThat(contarChunks(id)).isEqualTo(quantidade);
        assertThat(jdbc.sql("SELECT quantidade_chunks FROM conhecimento WHERE id = :id")
                .param("id", UUID.fromString(id)).query(Integer.class).single()).isEqualTo(quantidade);
        assertThat(jdbc.sql("SELECT count(*) FROM vector_store WHERE embedding IS NULL").query(Long.class).single())
                .isZero();
    }

    @Test
    void falhaNoSegundoLoteDeEmbeddingsDesfazTudo() {   // CA03.2
        embeddings.falharAPartirDaChamada(2);

        var resultado = cadastrar(textoLongo(12_000)); // > 8.191 tokens: o PgVectorStore faz ao menos 2 lotes

        assertThat(resultado).hasStatus(503);
        assertThat(embeddings.chamadas()).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.sql("SELECT count(*) FROM conhecimento").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM vector_store").query(Long.class).single()).isZero();
    }

    @Test
    void falhaDoProvedorGera503SemDetalhesInternos() throws Exception {   // CA01.6
        embeddings.falharAPartirDaChamada(1);

        var resultado = cadastrar("Conteúdo curto sobre o curso.");

        assertThat(resultado).hasStatus(503);
        assertThat(resultado.getResponse().getHeader("Retry-After")).isEqualTo("5");
        assertThat(resultado.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("EMBEDDING_INDISPONIVEL");
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("falha simulada", "detalhe interno", "IllegalStateException");
        assertThat(jdbc.sql("SELECT count(*) FROM conhecimento").query(Long.class).single()).isZero();
    }

    private MvcTestResult cadastrar(String conteudo) {
        String corpo = json.writeValueAsString(Map.of(
                "titulo", "Curso Java Web Developer",
                "conteudo", conteudo,
                "categoria", "CURSO",
                "fonte", "https://www.cotiinformatica.com.br/cursos/java-web-developer"));
        return mvc.post().uri("/api/conhecimentos").header(AdminTokenFilter.HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(corpo).exchange();
    }

    private long contarChunks(String documentoId) {
        return jdbc.sql("SELECT count(*) FROM vector_store WHERE metadata->>'documentoId' = :id")
                .param("id", documentoId).query(Long.class).single();
    }

    /** Aproximadamente {@code palavras} palavras (≈ 1,3 token cada). */
    private static String textoLongo(int palavras) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; sb.length() < palavras * 6; i++) {
            sb.append("Turma ").append(i).append(" estuda Java e Spring. ");
        }
        return sb.toString();
    }
}

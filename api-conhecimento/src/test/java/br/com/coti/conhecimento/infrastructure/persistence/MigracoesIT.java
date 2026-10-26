package br.com.coti.conhecimento.infrastructure.persistence;

import br.com.coti.conhecimento.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** As migrações rodam do zero no container vazio ao subir o contexto (CA03.4, parte da Fase 3). */
@IntegrationTest
class MigracoesIT {

    @Autowired
    private JdbcClient jdbc;

    @Test
    void extensoesInstaladas() {
        List<String> extensoes = jdbc.sql("SELECT extname FROM pg_extension").query(String.class).list();

        assertThat(extensoes).contains("vector", "hstore", "uuid-ossp");
    }

    @Test
    void tabelaConhecimentoComColunasDoContrato() {
        List<String> colunas = jdbc.sql("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'conhecimento'
                """).query(String.class).list();

        assertThat(colunas).containsExactlyInAnyOrder("id", "titulo", "conteudo", "categoria", "fonte", "tags",
                "quantidade_chunks", "tamanho_conteudo", "criado_em");
    }

    @Test
    void indicePorCategoriaEData() {
        String definicao = jdbc.sql("""
                SELECT indexdef FROM pg_indexes
                WHERE tablename = 'conhecimento' AND indexname = 'idx_conhecimento_categoria_criado_em'
                """).query(String.class).single();

        assertThat(definicao).contains("(categoria, criado_em DESC)");
    }

    @Test
    void tabelaVetorialComDimensao1536EIndiceHnswCosseno() {
        String tipo = jdbc.sql("""
                SELECT format_type(atttypid, atttypmod) FROM pg_attribute
                WHERE attrelid = 'vector_store'::regclass AND attname = 'embedding'
                """).query(String.class).single();
        String indice = jdbc.sql("""
                SELECT indexdef FROM pg_indexes
                WHERE tablename = 'vector_store' AND indexname = 'idx_vector_store_embedding'
                """).query(String.class).single();

        assertThat(tipo).isEqualTo("vector(1536)");
        assertThat(indice).containsIgnoringCase("USING hnsw").contains("vector_cosine_ops");
    }

    @Test
    void categoriaForaDoEnumERejeitadaPeloBanco() {
        Integer total = jdbc.sql("""
                SELECT count(*) FROM information_schema.check_constraints
                WHERE check_clause LIKE '%INSTITUCIONAL%'
                """).query(Integer.class).single();

        assertThat(total).isEqualTo(1);
    }
}

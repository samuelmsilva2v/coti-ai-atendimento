package br.com.coti.conhecimento.infrastructure.persistence;

import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.ResumoConhecimento;
import br.com.coti.conhecimento.domain.port.ConhecimentoRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcConhecimentoRepository implements ConhecimentoRepository {

    private static final String COLUNAS_RESUMO =
            "id, titulo, categoria, fonte, tags, quantidade_chunks, tamanho_conteudo, criado_em";

    private final JdbcClient jdbc;

    JdbcConhecimentoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void inserir(Conhecimento c) {
        jdbc.sql("""
                INSERT INTO conhecimento
                    (id, titulo, conteudo, categoria, fonte, tags, quantidade_chunks, tamanho_conteudo, criado_em)
                VALUES (:id, :titulo, :conteudo, :categoria, :fonte, :tags, :quantidadeChunks, :tamanhoConteudo, :criadoEm)
                """)
                .param("id", c.id())
                .param("titulo", c.titulo())
                .param("conteudo", c.conteudo())
                .param("categoria", c.categoria().name())
                .param("fonte", c.fonte())
                .param("tags", c.tags().toArray(String[]::new))
                .param("quantidadeChunks", c.quantidadeChunks())
                .param("tamanhoConteudo", c.tamanhoConteudo())
                .param("criadoEm", OffsetDateTime.ofInstant(c.criadoEm(), ZoneOffset.UTC))
                .update();
    }

    @Override
    public Optional<Conhecimento> buscarPorId(UUID id) {
        return jdbc.sql("SELECT " + COLUNAS_RESUMO + ", conteudo FROM conhecimento WHERE id = :id")
                .param("id", id)
                .query((rs, n) -> new Conhecimento(
                        rs.getObject("id", UUID.class),
                        rs.getString("titulo"),
                        rs.getString("conteudo"),
                        Categoria.valueOf(rs.getString("categoria")),
                        rs.getString("fonte"),
                        tags(rs),
                        rs.getInt("quantidade_chunks"),
                        rs.getInt("tamanho_conteudo"),
                        rs.getObject("criado_em", OffsetDateTime.class).toInstant()))
                .optional();
    }

    @Override
    public List<ResumoConhecimento> listar(Optional<Categoria> categoria, int pagina, int tamanho) {
        return jdbc.sql("SELECT " + COLUNAS_RESUMO + " FROM conhecimento"
                        + filtro(categoria)
                        + " ORDER BY criado_em DESC, id LIMIT :limite OFFSET :deslocamento")
                .paramSource(parametros(categoria)
                        .addValue("limite", tamanho)
                        .addValue("deslocamento", (long) pagina * tamanho))
                .query((rs, n) -> new ResumoConhecimento(
                        rs.getObject("id", UUID.class),
                        rs.getString("titulo"),
                        Categoria.valueOf(rs.getString("categoria")),
                        rs.getString("fonte"),
                        tags(rs),
                        rs.getInt("quantidade_chunks"),
                        rs.getInt("tamanho_conteudo"),
                        rs.getObject("criado_em", OffsetDateTime.class).toInstant()))
                .list();
    }

    @Override
    public long contar(Optional<Categoria> categoria) {
        return jdbc.sql("SELECT count(*) FROM conhecimento" + filtro(categoria))
                .paramSource(parametros(categoria))
                .query(Long.class)
                .single();
    }

    private static String filtro(Optional<Categoria> categoria) {
        return categoria.isPresent() ? " WHERE categoria = :categoria" : "";
    }

    private static MapSqlParameterSource parametros(Optional<Categoria> categoria) {
        var parametros = new MapSqlParameterSource();
        categoria.ifPresent(c -> parametros.addValue("categoria", c.name()));
        return parametros;
    }

    private static List<String> tags(ResultSet rs) throws SQLException {
        Array array = rs.getArray("tags");
        return array == null ? List.of() : Arrays.asList((String[]) array.getArray());
    }
}

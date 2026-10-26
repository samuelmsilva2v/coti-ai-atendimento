package br.com.coti.conhecimento.infrastructure.persistence;

import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.ResumoConhecimento;
import br.com.coti.conhecimento.domain.port.ConhecimentoRepository;
import br.com.coti.conhecimento.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class JdbcConhecimentoRepositoryIT {

    private static final Instant BASE = Instant.parse("2026-10-25T18:30:00.123Z");

    @Autowired
    private ConhecimentoRepository repositorio;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void limpar() {
        jdbc.sql("TRUNCATE conhecimento").update();
    }

    @Test
    void insereELeTodosOsCampos() {
        var original = conhecimento(Categoria.CURSO, BASE, List.of("java", "spring"));

        repositorio.inserir(original);

        assertThat(repositorio.buscarPorId(original.id())).contains(original);
    }

    @Test
    void tagsVaziasSaoPreservadas() {
        var original = conhecimento(Categoria.INSTITUCIONAL, BASE, List.of());

        repositorio.inserir(original);

        assertThat(repositorio.buscarPorId(original.id()).orElseThrow().tags()).isEmpty();
    }

    @Test
    void idInexistenteRetornaVazio() {
        assertThat(repositorio.buscarPorId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void listaPaginadoEmOrdemDecrescenteDeCriacao() {
        for (int i = 0; i < 5; i++) {
            repositorio.inserir(conhecimento(Categoria.CURSO, BASE.plus(i, ChronoUnit.MINUTES), List.of()));
        }

        List<ResumoConhecimento> primeira = repositorio.listar(Optional.empty(), 0, 2);
        List<ResumoConhecimento> ultima = repositorio.listar(Optional.empty(), 2, 2);

        assertThat(primeira).extracting(ResumoConhecimento::criadoEm)
                .containsExactly(BASE.plus(4, ChronoUnit.MINUTES), BASE.plus(3, ChronoUnit.MINUTES));
        assertThat(ultima).extracting(ResumoConhecimento::criadoEm).containsExactly(BASE);
        assertThat(repositorio.contar(Optional.empty())).isEqualTo(5);
    }

    @Test
    void filtraPorCategoria() {
        repositorio.inserir(conhecimento(Categoria.CURSO, BASE, List.of()));
        repositorio.inserir(conhecimento(Categoria.MODALIDADE, BASE, List.of()));
        repositorio.inserir(conhecimento(Categoria.CURSO, BASE.plusSeconds(1), List.of()));

        assertThat(repositorio.listar(Optional.of(Categoria.CURSO), 0, 20))
                .hasSize(2)
                .allMatch(r -> r.categoria() == Categoria.CURSO);
        assertThat(repositorio.contar(Optional.of(Categoria.CURSO))).isEqualTo(2);
        assertThat(repositorio.contar(Optional.of(Categoria.PROGRAMA))).isZero();
    }

    private static Conhecimento conhecimento(Categoria categoria, Instant criadoEm, List<String> tags) {
        String conteudo = "Conteúdo com acentuação: ção, ã, é.";
        return new Conhecimento(UUID.randomUUID(), "Título " + categoria, conteudo, categoria,
                "https://www.cotiinformatica.com.br/teste", tags, 0, conteudo.length(), criadoEm);
    }
}

package br.com.coti.conhecimento.controller;

import br.com.coti.conhecimento.infrastructure.web.AdminTokenFilter;
import br.com.coti.conhecimento.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Fluxo REST completo contra o PostgreSQL real (Testcontainers). */
@IntegrationTest
class ConhecimentoApiIT {

    private static final String TOKEN = "token-de-teste-0123456789";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void limpar() {
        jdbc.sql("TRUNCATE conhecimento").update();
    }

    @Test
    void cadastroPersisteEPodeSerConsultadoPorId() throws Exception {   // CA01.1 (parcial) e CA01.8
        var criado = cadastrar("Curso Java Web Developer", "CURSO");

        assertThat(criado).hasStatus(201);
        String location = criado.getResponse().getHeader("Location");
        String id = assertThat(criado).bodyJson().extractingPath("$.id").asString().actual();
        assertThat(location).endsWith("/api/conhecimentos/" + id);
        assertThat(jdbc.sql("SELECT count(*) FROM conhecimento WHERE id = :id").param("id", UUID.fromString(id))
                .query(Long.class).single()).isEqualTo(1);

        var consultado = get("/api/conhecimentos/" + id);

        assertThat(consultado).hasStatusOk();
        var json = assertThat(consultado).bodyJson();
        json.extractingPath("$.conteudo").isEqualTo("Pré-requisito: lógica de programação.");
        json.extractingPath("$.tags[0]").isEqualTo("java");
        json.extractingPath("$.tamanhoConteudo").isEqualTo(37);
        json.extractingPath("$.criadoEm").asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z");
    }

    @Test
    void conteudoNoLimiteDe100MilCaracteresEAceito() {   // T063: limite de corpo compatível com o contrato
        String conteudo = ("Aula de Java e Spring. ").repeat(5_000).substring(0, 100_000);
        String corpo = "{\"titulo\":\"Grande\",\"conteudo\":\"" + conteudo
                + "\",\"categoria\":\"CURSO\",\"fonte\":\"https://coti\"}";

        var resultado = mvc.post().uri("/api/conhecimentos").header(AdminTokenFilter.HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(corpo).exchange();

        assertThat(resultado).hasStatus(201);
        assertThat(resultado).bodyJson().extractingPath("$.tamanhoConteudo").isEqualTo(100_000);
    }

    @Test
    void idInexistenteGera404() {
        var resultado = get("/api/conhecimentos/" + UUID.randomUUID());

        assertThat(resultado).hasStatus(404);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("RECURSO_NAO_ENCONTRADO");
    }

    @Test
    void listagemPaginadaEFiltradaSemConteudo() {   // CA01.7
        for (int i = 0; i < 20; i++) {
            cadastrar("Institucional " + i, "INSTITUCIONAL");
        }
        for (int i = 0; i < 5; i++) {
            cadastrar("Curso " + i, "CURSO");
        }

        var resultado = get("/api/conhecimentos?categoria=CURSO&tamanho=20");

        assertThat(resultado).hasStatusOk();
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.itens.length()").isEqualTo(5);
        json.extractingPath("$.totalItens").isEqualTo(5);
        json.extractingPath("$.totalPaginas").isEqualTo(1);
        json.extractingPath("$.itens[*].categoria").asArray().containsOnly("CURSO");
        json.doesNotHavePath("$.itens[0].conteudo");

        var todos = get("/api/conhecimentos?tamanho=10&pagina=2");
        assertThat(todos).bodyJson().extractingPath("$.itens.length()").isEqualTo(5);
        assertThat(todos).bodyJson().extractingPath("$.totalItens").isEqualTo(25);
        assertThat(todos).bodyJson().extractingPath("$.totalPaginas").isEqualTo(3);
    }

    private MvcTestResult cadastrar(String titulo, String categoria) {
        String corpo = """
                {"titulo":"%s","conteudo":"Pré-requisito: lógica de programação.","categoria":"%s",
                 "fonte":"https://www.cotiinformatica.com.br","tags":["java"]}
                """.formatted(titulo, categoria);
        return mvc.post().uri("/api/conhecimentos").header(AdminTokenFilter.HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON).content(corpo).exchange();
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).header(AdminTokenFilter.HEADER, TOKEN).exchange();
    }
}

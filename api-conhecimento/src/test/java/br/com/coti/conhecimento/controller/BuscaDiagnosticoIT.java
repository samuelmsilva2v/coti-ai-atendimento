package br.com.coti.conhecimento.controller;

import br.com.coti.conhecimento.application.BuscaSemanticaService;
import br.com.coti.conhecimento.application.CadastrarConhecimentoService;
import br.com.coti.conhecimento.domain.model.ConsultaBusca;
import br.com.coti.conhecimento.infrastructure.web.AdminTokenFilter;
import br.com.coti.conhecimento.support.FakeEmbeddingModel;
import br.com.coti.conhecimento.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static br.com.coti.conhecimento.support.BaseDeTeste.JAVA;
import static br.com.coti.conhecimento.support.BaseDeTeste.JAVA_PARCIAL;
import static br.com.coti.conhecimento.support.BaseDeTeste.PERGUNTA_JAVA;
import static br.com.coti.conhecimento.support.BaseDeTeste.cadastrar;
import static br.com.coti.conhecimento.support.BaseDeTeste.limpar;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class BuscaDiagnosticoIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private CadastrarConhecimentoService cadastro;

    @Autowired
    private BuscaSemanticaService busca;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FakeEmbeddingModel embeddings;

    @BeforeEach
    void preparar() {
        limpar(jdbc);
        embeddings.reiniciar();
        cadastrar(cadastro, "Curso Java Web Developer", JAVA);
        cadastrar(cadastro, "Turma Java Noturna", JAVA_PARCIAL);
    }

    @Test
    void endpointDevolveOMesmoResultadoDoServico() {
        var esperado = busca.buscar(ConsultaBusca.de(PERGUNTA_JAVA, 5));

        var resultado = mvc.post().uri("/api/conhecimentos/busca")
                .header(AdminTokenFilter.HEADER, "token-de-teste-0123456789")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pergunta\":\"" + PERGUNTA_JAVA + "\",\"limite\":5}")
                .exchange();

        assertThat(resultado).hasStatusOk();
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.total").isEqualTo(esperado.trechos().size());
        json.extractingPath("$.trechos[*].chunkId").asArray()
                .containsExactlyElementsOf(esperado.trechos().stream().map(t -> t.chunkId().toString()).toList());
    }

    @Test
    void entradaInvalidaNaoGeraEmbedding() {   // CA04.4
        int antes = embeddings.chamadas();

        var resultado = mvc.post().uri("/api/conhecimentos/busca")
                .header(AdminTokenFilter.HEADER, "token-de-teste-0123456789")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pergunta\":\"\",\"limite\":50}")
                .exchange();

        assertThat(resultado).hasStatus(400);
        assertThat(embeddings.chamadas()).isEqualTo(antes);
    }
}

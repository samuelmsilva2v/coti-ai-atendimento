package br.com.coti.conhecimento.controller;

import br.com.coti.conhecimento.application.BuscaSemanticaService;
import br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException;
import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.ConsultaBusca;
import br.com.coti.conhecimento.domain.model.ResultadoBusca;
import br.com.coti.conhecimento.domain.model.Trecho;
import br.com.coti.conhecimento.infrastructure.web.AdminTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebMvcTest(BuscaDiagnosticoController.class)
class BuscaDiagnosticoControllerTest {

    private static final String TOKEN = "token-de-teste-0123456789";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private BuscaSemanticaService busca;

    @Test
    void buscaDevolveTrechosTotalELimiar() {
        var trecho = new Trecho(UUID.randomUUID(), "Java 21 e Spring Boot.", UUID.randomUUID(), "Curso Java",
                "https://coti", Categoria.CURSO, 0.8731);
        when(busca.buscar(ConsultaBusca.de("Quais tecnologias?", 3)))
                .thenReturn(new ResultadoBusca(List.of(trecho), 0.70));

        var resultado = post("{\"pergunta\":\"Quais tecnologias?\",\"limite\":3}", TOKEN);

        assertThat(resultado).hasStatusOk();
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.total").isEqualTo(1);
        json.extractingPath("$.limiarSimilaridade").isEqualTo(0.70);
        json.extractingPath("$.trechos[0].chunkId").isEqualTo(trecho.chunkId().toString());
        json.extractingPath("$.trechos[0].texto").isEqualTo("Java 21 e Spring Boot.");
        json.extractingPath("$.trechos[0].documentoId").isEqualTo(trecho.documentoId().toString());
        json.extractingPath("$.trechos[0].titulo").isEqualTo("Curso Java");
        json.extractingPath("$.trechos[0].fonte").isEqualTo("https://coti");
        json.extractingPath("$.trechos[0].categoria").isEqualTo("CURSO");
        json.extractingPath("$.trechos[0].similaridade").isEqualTo(0.8731);
    }

    @Test
    void limitePadraoE5() {
        when(busca.buscar(any())).thenReturn(new ResultadoBusca(List.of(), 0.70));

        assertThat(post("{\"pergunta\":\"Quais tecnologias?\"}", TOKEN)).hasStatusOk();

        verify(busca).buscar(ConsultaBusca.de("Quais tecnologias?", 5));
    }

    @Test
    void entradaInvalidaGera400SemBuscar() {
        assertErro(post("{\"pergunta\":\"   \"}", TOKEN), 400, "VALIDACAO_FALHOU");
        assertErro(post("{\"pergunta\":\"x\",\"limite\":11}", TOKEN), 400, "VALIDACAO_FALHOU");
        assertErro(post("{\"pergunta\":\"" + "x".repeat(1001) + "\"}", TOKEN), 400, "VALIDACAO_FALHOU");
        assertErro(post("{\"pergunta\":", TOKEN), 400, "JSON_INVALIDO");
        verifyNoInteractions(busca);
    }

    @Test
    void semTokenGera401() {
        assertErro(post("{\"pergunta\":\"x\"}", null), 401, "NAO_AUTORIZADO");
        verifyNoInteractions(busca);
    }

    @Test
    void dependenciaIndisponivelGera503() {
        when(busca.buscar(any())).thenThrow(new DependenciaIndisponivelException(
                DependenciaIndisponivelException.Dependencia.EMBEDDING, new RuntimeException("provedor caiu")));

        var resultado = post("{\"pergunta\":\"x\"}", TOKEN);

        assertErro(resultado, 503, "EMBEDDING_INDISPONIVEL");
        assertThat(resultado.getResponse().getHeader("Retry-After")).isEqualTo("5");
    }

    private MvcTestResult post(String corpo, String token) {
        var requisicao = mvc.post().uri("/api/conhecimentos/busca").contentType(MediaType.APPLICATION_JSON).content(corpo);
        return token == null ? requisicao.exchange() : requisicao.header(AdminTokenFilter.HEADER, token).exchange();
    }

    private static void assertErro(MvcTestResult resultado, int status, String codigo) {
        assertThat(resultado).hasStatus(status);
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
    }
}

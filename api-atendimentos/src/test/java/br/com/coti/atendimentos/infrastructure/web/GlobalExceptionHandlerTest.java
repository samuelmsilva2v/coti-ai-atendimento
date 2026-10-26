package br.com.coti.atendimentos.infrastructure.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(GlobalExceptionHandlerTest.ControllerDeTeste.class)
@Import(GlobalExceptionHandlerTest.ControllerDeTeste.class)
class GlobalExceptionHandlerTest {

    private static final String CORRELATION_ID = "8d2f9a4e-6b1c-4f3a-9e7d-2c5b8a1f0e34";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void validacaoDeCorpoGera400ComErrosPorCampo() throws Exception {
        var resultado = mvc.post().uri("/teste/validacao")
                .header(CorrelationIdFilter.HEADER, CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":\"\"}")
                .exchange();

        assertProblema(resultado, 400, "VALIDACAO_FALHOU", "/teste/validacao");
        assertThat(resultado).bodyJson().extractingPath("$.correlationId").isEqualTo(CORRELATION_ID);
        assertThat(resultado).bodyJson().extractingPath("$.erros[0].campo").isEqualTo("nome");
        assertThat(resultado).bodyJson().extractingPath("$.erros[0].mensagem").isNotNull();
        assertThat(resultado.getResponse().getHeader(CorrelationIdFilter.HEADER)).isEqualTo(CORRELATION_ID);
    }

    @Test
    void jsonMalformadoGera400JsonInvalido() throws Exception {
        var resultado = mvc.post().uri("/teste/validacao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\":")
                .exchange();

        assertProblema(resultado, 400, "JSON_INVALIDO", "/teste/validacao");
    }

    @Test
    void parametroDeCaminhoInvalidoGera400() throws Exception {
        var resultado = mvc.get().uri("/teste/item/nao-e-uuid").exchange();

        assertProblema(resultado, 400, "VALIDACAO_FALHOU", "/teste/item/nao-e-uuid");
        assertThat(resultado).bodyJson().extractingPath("$.erros[0].campo").isEqualTo("id");
    }

    @Test
    void rotaInexistenteGera404() throws Exception {
        assertProblema(mvc.get().uri("/nao-existe").exchange(), 404, "RECURSO_NAO_ENCONTRADO", "/nao-existe");
    }

    @Test
    void metodoNaoSuportadoGera405() throws Exception {
        assertProblema(mvc.get().uri("/teste/validacao").exchange(), 405, "METODO_NAO_SUPORTADO", "/teste/validacao");
    }

    @Test
    void tipoDeMidiaNaoSuportadoGera415() throws Exception {
        var resultado = mvc.post().uri("/teste/validacao")
                .contentType(MediaType.TEXT_PLAIN)
                .content("nome")
                .exchange();

        assertProblema(resultado, 415, "TIPO_MIDIA_NAO_SUPORTADO", "/teste/validacao");
    }

    @Test
    void erroInesperadoGera500SemVazarDetalhes() throws Exception {
        var resultado = mvc.get().uri("/teste/erro").exchange();

        assertProblema(resultado, 500, "ERRO_INTERNO", "/teste/erro");
        String corpo = resultado.getResponse().getContentAsString();
        assertThat(corpo).doesNotContain("detalhe interno secreto", "IllegalStateException", "at br.com");
    }

    private static void assertProblema(MvcTestResult resultado, int status, String codigo, String instancia)
            throws Exception {
        assertThat(resultado).hasStatus(status);
        assertThat(resultado.getResponse().getContentType()).startsWith("application/problem+json");
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.status").isEqualTo(status);
        json.extractingPath("$.codigo").isEqualTo(codigo);
        json.extractingPath("$.type")
                .isEqualTo("https://coti.com.br/problemas/" + codigo.toLowerCase().replace('_', '-'));
        json.extractingPath("$.title").isNotNull();
        json.extractingPath("$.detail").isNotNull();
        json.extractingPath("$.instance").isEqualTo(instancia);
        json.extractingPath("$.timestamp").asString().endsWith("Z");
        assertThat(UUID.fromString(json.extractingPath("$.correlationId").asString().actual())).isNotNull();
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("trace", "exception");
    }

    @RestController
    @RequestMapping("/teste")
    static class ControllerDeTeste {

        record Entrada(@NotBlank @Size(max = 5) String nome) {
        }

        @PostMapping("/validacao")
        void validar(@Valid @RequestBody Entrada entrada) {
        }

        @GetMapping("/item/{id}")
        void item(@PathVariable UUID id) {
        }

        @GetMapping("/erro")
        void erro() {
            throw new IllegalStateException("detalhe interno secreto");
        }
    }
}

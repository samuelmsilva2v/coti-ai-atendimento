package br.com.coti.conhecimento.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(AdminTokenFilterTest.ControllerDeTeste.class)
@Import(AdminTokenFilterTest.ControllerDeTeste.class)
@ExtendWith(OutputCaptureExtension.class)
class AdminTokenFilterTest {

    /** Mesmo valor de src/test/resources/application.properties. */
    private static final String TOKEN = "token-de-teste-0123456789";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void semTokenGera401NoFormatoDoContrato() {
        var resultado = mvc.get().uri("/api/conhecimentos/ping").exchange();

        assertThat(resultado).hasStatus(401);
        assertThat(resultado.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(resultado.getResponse().getHeader("WWW-Authenticate")).isEqualTo(AdminTokenFilter.HEADER);
        assertThat(resultado.getResponse().getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo("NAO_AUTORIZADO");
        assertThat(resultado).bodyJson().extractingPath("$.correlationId").isNotNull();
    }

    @Test
    void tokenErradoGera401SemVazarOValor(CapturedOutput saida) throws Exception {
        var resultado = mvc.get().uri("/api/conhecimentos")
                .header(AdminTokenFilter.HEADER, "token-errado-que-nao-pode-vazar")
                .exchange();

        assertThat(resultado).hasStatus(401);
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("token-errado-que-nao-pode-vazar");
        assertThat(saida.getAll()).doesNotContain("token-errado-que-nao-pode-vazar", TOKEN);
    }

    @Test
    void tokenCorretoLiberaARequisicao() {
        var resultado = mvc.get().uri("/api/conhecimentos/ping").header(AdminTokenFilter.HEADER, TOKEN).exchange();

        assertThat(resultado).hasStatusOk().hasBodyTextEqualTo("pong");
    }

    @Test
    void autorizacaoAconteceAntesDaValidacaoDoCorpo() {
        var resultado = mvc.post().uri("/api/conhecimentos/eco")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{json quebrado")
                .exchange();

        assertThat(resultado).hasStatus(401);
    }

    @Test
    void rotasForaDoPrefixoNaoExigemToken() {
        assertThat(mvc.get().uri("/publico").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/api/conhecimentosx").exchange()).hasStatusOk();
    }

    @RestController
    static class ControllerDeTeste {

        @GetMapping("/api/conhecimentos/ping")
        String ping() {
            return "pong";
        }

        @GetMapping("/api/conhecimentos")
        String raiz() {
            return "ok";
        }

        @PostMapping("/api/conhecimentos/eco")
        String eco(@RequestBody String corpo) {
            return corpo;
        }

        @GetMapping({"/publico", "/api/conhecimentosx"})
        String publico() {
            return "ok";
        }
    }
}

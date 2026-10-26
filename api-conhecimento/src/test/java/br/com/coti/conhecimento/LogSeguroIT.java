package br.com.coti.conhecimento;

import br.com.coti.conhecimento.infrastructure.web.AdminTokenFilter;
import br.com.coti.conhecimento.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/** Logs só com metadados (Art. 13, RNF02.2): conteúdo, pergunta e token nunca aparecem. */
@IntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class LogSeguroIT {

    private static final String TOKEN = "token-de-teste-0123456789";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void cadastroEBuscaNaoRegistramConteudoNemPerguntaNemToken(CapturedOutput saida) {
        String marcadorConteudo = "MARCADOR-CONTEUDO-5d1f0a";
        String marcadorPergunta = "MARCADOR-PERGUNTA-93be72";
        String correlationId = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d";

        var cadastro = mvc.post().uri("/api/conhecimentos").header(AdminTokenFilter.HEADER, TOKEN)
                .header("X-Correlation-Id", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Curso\",\"conteudo\":\"Texto " + marcadorConteudo
                        + "\",\"categoria\":\"CURSO\",\"fonte\":\"https://coti\"}")
                .exchange();
        var busca = mvc.post().uri("/api/conhecimentos/busca").header(AdminTokenFilter.HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pergunta\":\"Pergunta " + marcadorPergunta + "\"}")
                .exchange();

        assertThat(cadastro).hasStatus(201);
        assertThat(busca).hasStatusOk();
        assertThat(saida.getAll()).doesNotContain(marcadorConteudo, marcadorPergunta, TOKEN, "test-key");
        assertThat(saida.getOut()).contains("Conhecimento cadastrado", "Busca semântica executada",
                "\"correlationId\":\"" + correlationId + "\"");
    }
}

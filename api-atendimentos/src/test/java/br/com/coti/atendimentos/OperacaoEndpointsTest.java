package br.com.coti.atendimentos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class OperacaoEndpointsTest {

    @Autowired
    private MockMvcTester mvc;

    /** Nos testes o MCP Server aponta para a porta 1 (sem servidor): health DOWN (contrato, seção 5.2). */
    @Test
    void healthDownSemDetalhesQuandoOMcpServerEstaForaDoAr() throws Exception {   // T047
        var resposta = mvc.get().uri("/actuator/health").exchange();

        assertThat(resposta).hasStatus(503);
        assertThat(resposta).bodyJson().extractingPath("$.status").isEqualTo("DOWN");
        assertThat(resposta).bodyJson().extractingPath("$.components.mcpConhecimento.status").isEqualTo("DOWN");
        assertThat(resposta).bodyJson().extractingPath("$.components.diskSpace.status").isEqualTo("UP");
        assertThat(resposta.getResponse().getContentAsString()).doesNotContain("\"details\"", "localhost");
    }

    @Test
    void openApiDisponivelComOEndpointDePerguntas() {
        var resposta = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(resposta).hasStatusOk();
        assertThat(resposta).bodyJson().extractingPath("$.openapi").asString().startsWith("3.");
        assertThat(resposta).bodyJson().extractingPath("$.paths").asMap()
                .containsKey("/api/atendimentos/perguntar")
                .doesNotContainKey("/mcp");
    }

    @Test
    void actuatorNaoExpoeEndpointsSensiveis() {
        for (String sensivel : java.util.List.of("/actuator/env", "/actuator/heapdump", "/actuator/threaddump", "/actuator/loggers", "/actuator/configprops", "/actuator/beans", "/actuator/mappings")) {
            assertThat(mvc.get().uri(sensivel).exchange()).as(sensivel).hasStatus4xxClientError();
        }
    }
}

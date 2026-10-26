package br.com.coti.atendimentos.infrastructure.mcp;

import br.com.coti.fakemcp.FakeMcpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/** /actuator/health UP com o MCP Server (falso) ativo; o caso DOWN está em OperacaoEndpointsTest. */
@SpringBootTest
@AutoConfigureMockMvc
class McpHealthIndicatorIT {

    private static final FakeMcpServer SERVIDOR = FakeMcpServer.iniciar();

    @DynamicPropertySource
    static void apontar(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.ai.mcp.client.streamable-http.connections.conhecimento.url", SERVIDOR::url);
    }

    @AfterAll
    static void parar() {
        SERVIDOR.close();
    }

    @Autowired
    private MockMvcTester mvc;

    @Test
    void healthUpComMcpServerDisponivel() throws Exception {
        var resposta = mvc.get().uri("/actuator/health").exchange();

        assertThat(resposta).hasStatusOk();
        assertThat(resposta).bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(resposta).bodyJson().extractingPath("$.components.mcpConhecimento.status").isEqualTo("UP");
        assertThat(resposta.getResponse().getContentAsString()).doesNotContain("\"details\"", "localhost");
    }
}

package br.com.coti.conhecimento;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import br.com.coti.conhecimento.support.IntegrationTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class OperacaoEndpointsIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void healthExpoeComponentesSemDetalhes() throws Exception {
        var resposta = mvc.get().uri("/actuator/health").exchange();

        assertThat(resposta).hasStatusOk();
        assertThat(resposta).bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(resposta).bodyJson().extractingPath("$.components").isNotNull();
        assertThat(resposta).bodyJson().extractingPath("$.components.db.status").isEqualTo("UP");   // T039
        assertThat(resposta.getResponse().getContentAsString()).doesNotContain("\"details\"");
    }

    @Test
    void openApiDisponivelSemOEndpointMcp() throws Exception {
        var resposta = mvc.get().uri("/v3/api-docs").exchange();

        assertThat(resposta).hasStatusOk();
        assertThat(resposta).bodyJson().extractingPath("$.openapi").asString().startsWith("3.");
        assertThat(resposta).bodyJson().extractingPath("$.paths").asMap()
                .containsKeys("/api/conhecimentos", "/api/conhecimentos/{id}", "/api/conhecimentos/busca")
                .doesNotContainKey("/mcp");
        assertThat(resposta.getResponse().getContentAsString()).doesNotContain("\"/mcp");
    }

    @Test
    void actuatorNaoExpoeEndpointsSensiveis() {
        for (String sensivel : java.util.List.of("/actuator/env", "/actuator/heapdump", "/actuator/threaddump", "/actuator/loggers", "/actuator/configprops", "/actuator/beans", "/actuator/mappings")) {
            assertThat(mvc.get().uri(sensivel).exchange()).as(sensivel).hasStatus4xxClientError();
        }
    }
}

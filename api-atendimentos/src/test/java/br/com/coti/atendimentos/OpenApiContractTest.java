package br.com.coti.atendimentos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** /v3/api-docs coerente com specs/06-api-contracts.md, seção 5 (Art. 15). */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiContractTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void documentaSoOEndpointDePerguntasComOsCodigosDoContrato() throws Exception {
        var resposta = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(resposta).hasStatusOk();
        JsonNode docs = JsonMapper.builder().build().readTree(resposta.getResponse().getContentAsString());

        assertThat(docs.get("paths").propertyNames()).containsExactly("/api/atendimentos/perguntar");
        assertThat(docs.toString()).doesNotContain("\"/mcp");
        JsonNode operacao = docs.at("/paths/~1api~1atendimentos~1perguntar/post");
        assertThat(operacao.get("responses").propertyNames()).contains("200", "400", "415", "503", "500");
        assertThat(operacao.at("/responses/503/content").has("application/problem+json")).isTrue();
        assertThat(operacao.at("/parameters/0/name").asString()).isEqualTo("X-Correlation-Id");
        assertThat(docs.at("/components/schemas/PerguntaRequest/properties/pergunta/example").asString())
                .isEqualTo("Quais os pré-requisitos do curso Java Web Developer?");
        assertThat(docs.at("/components/schemas/RespostaAtendimentoResponse/properties").propertyNames())
                .containsExactlyInAnyOrder("correlationId", "resposta", "respondida", "fallback", "fontes", "respondidoEm");
    }
}

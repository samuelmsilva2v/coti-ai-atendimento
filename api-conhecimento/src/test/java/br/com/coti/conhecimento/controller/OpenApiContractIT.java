package br.com.coti.conhecimento.controller;

import br.com.coti.conhecimento.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** /v3/api-docs coerente com specs/06-api-contracts.md, seção 3 (Art. 15). */
@IntegrationTest
class OpenApiContractIT {

    private static final Map<String, Set<String>> OPERACOES = Map.of(
            "post /api/conhecimentos", Set.of("201", "400", "401", "415", "503", "500"),
            "get /api/conhecimentos", Set.of("200", "400", "401", "503"),
            "get /api/conhecimentos/{id}", Set.of("200", "400", "401", "404", "503"),
            "post /api/conhecimentos/busca", Set.of("200", "400", "401", "503"));

    @Autowired
    private MockMvcTester mvc;

    @Test
    void documentaExatamenteOsEndpointsDoContratoSemOMcp() throws Exception {
        JsonNode docs = docs();

        assertThat(docs.get("paths").propertyNames())
                .containsExactlyInAnyOrder("/api/conhecimentos", "/api/conhecimentos/{id}", "/api/conhecimentos/busca");
        assertThat(docs.toString()).doesNotContain("\"/mcp");
    }

    @Test
    void cadaOperacaoDocumentaOsCodigosDoContratoEErrosComoProblemJson() throws Exception {
        JsonNode docs = docs();

        OPERACOES.forEach((operacao, codigos) -> {
            String[] partes = operacao.split(" ");
            JsonNode respostas = docs.at("/paths/" + partes[1].replace("/", "~1") + "/" + partes[0] + "/responses");
            assertThat(respostas.propertyNames()).as(operacao).containsAll(codigos);
            assertThat(respostas.at("/400/content").has("application/problem+json")).as(operacao).isTrue();
        });
    }

    @Test
    void tokenAdministrativoEExemplosDocumentados() throws Exception {
        JsonNode docs = docs();

        JsonNode esquema = docs.at("/components/securitySchemes/adminToken");
        assertThat(esquema.get("type").asString()).isEqualTo("apiKey");
        assertThat(esquema.get("in").asString()).isEqualTo("header");
        assertThat(esquema.get("name").asString()).isEqualTo("X-Admin-Token");
        assertThat(docs.at("/paths/~1api~1conhecimentos/post/security/0").has("adminToken")).isTrue();
        assertThat(docs.at("/components/schemas/CadastrarConhecimentoRequest/properties/titulo/example").asString())
                .isEqualTo("Curso Java Web Developer");
    }

    private JsonNode docs() throws Exception {
        var resposta = mvc.get().uri("/v3/api-docs").exchange();
        assertThat(resposta).hasStatusOk();
        return JsonMapper.builder().build().readTree(resposta.getResponse().getContentAsString());
    }
}

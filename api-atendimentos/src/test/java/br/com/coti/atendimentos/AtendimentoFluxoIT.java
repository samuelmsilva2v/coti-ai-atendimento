package br.com.coti.atendimentos;

import br.com.coti.atendimentos.support.StubChatModel;
import br.com.coti.atendimentos.support.StubChatModelConfig;
import br.com.coti.fakemcp.FakeMcpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fluxo B ponta a ponta (contrato, seção 5.1): REST → MCP (servidor falso) → contexto → modelo (stub) →
 * resposta. Nenhuma chamada à OpenAI real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(StubChatModelConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AtendimentoFluxoIT {

    private static final FakeMcpServer MCP = FakeMcpServer.iniciar();
    private static final String CORRELATION_ID = "8d2f9a4e-6b1c-4f3a-9e7d-2c5b8a1f0e34";

    @DynamicPropertySource
    static void apontar(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.ai.mcp.client.streamable-http.connections.conhecimento.url", MCP::url);
    }

    @AfterAll
    static void parar() {
        MCP.close();
    }

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private StubChatModel modelo;

    @BeforeEach
    void reiniciar() {
        MCP.comportamento().reiniciar();
        modelo.reiniciar();
    }

    @Test
    void respostaFundamentadaComFontesNoFormatoDoContrato() {   // CA06.1, CA08.1, CA09.1
        Map<String, Object> trecho = trecho("Pré-requisito: lógica de programação.", 0.8912);
        MCP.comportamento().responderTrechos(List.of(trecho));
        modelo.responder("{\"resposta\":\"O pré-requisito é lógica de programação.\",\"respondida\":true,\"fontesUsadas\":[\"T1\"]}");

        var resultado = perguntar();

        assertThat(resultado).hasStatusOk();
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.correlationId").isEqualTo(CORRELATION_ID);
        json.extractingPath("$.resposta").isEqualTo("O pré-requisito é lógica de programação.");
        json.extractingPath("$.respondida").isEqualTo(true);
        json.extractingPath("$.fallback").isEqualTo(false);
        json.extractingPath("$.fontes[0].chunkId").isEqualTo(trecho.get("chunkId"));
        json.extractingPath("$.fontes[0].documentoId").isEqualTo(trecho.get("documentoId"));
        json.extractingPath("$.fontes[0].titulo").isEqualTo("Curso Java Web Developer");
        json.extractingPath("$.fontes[0].fonte").isEqualTo("https://www.cotiinformatica.com.br/cursos/java-web-developer");
        json.extractingPath("$.fontes[0].categoria").isEqualTo("CURSO");
        json.extractingPath("$.fontes[0].similaridade").isEqualTo(0.8912);
        json.doesNotHavePath("$.fontes[0].texto");
        assertThat(MCP.comportamento().recebidas().getFirst().meta()).containsEntry("correlationId", CORRELATION_ID);
        assertThat(modelo.recebidos()).hasSize(1);
    }

    @Test
    void somenteAsFontesUsadasPeloModeloSaoDevolvidas() {   // CA09.2
        List<Map<String, Object>> cinco = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cinco.add(trecho("Trecho " + i, 0.9 - i / 100.0));
        }
        MCP.comportamento().responderTrechos(cinco);
        modelo.responder("{\"resposta\":\"r\",\"respondida\":true,\"fontesUsadas\":[\"T2\",\"T4\"]}");

        var json = assertThat(perguntar()).bodyJson();

        json.extractingPath("$.fontes.length()").isEqualTo(2);
        json.extractingPath("$.fontes[0].chunkId").isEqualTo(cinco.get(1).get("chunkId"));
        json.extractingPath("$.fontes[1].chunkId").isEqualTo(cinco.get(3).get("chunkId"));
    }

    @Test
    void semEvidenciaFallbackSemChamarOModelo() {   // CA09.3, CA10.1, CA10.2
        MCP.comportamento().responderVazio();

        var resultado = perguntar();

        assertThat(resultado).hasStatusOk();
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.respondida").isEqualTo(false);
        json.extractingPath("$.fallback").isEqualTo(true);
        json.extractingPath("$.fontes").asArray().isEmpty();
        json.extractingPath("$.resposta").asString().startsWith("Não encontrei essa informação");
        assertThat(modelo.recebidos()).isEmpty();
    }

    @Test
    void fonteInventadaPeloModeloViraFallback() {   // CA08.3
        MCP.comportamento().responderTrechos(List.of(trecho("Conteúdo do curso.", 0.9)));
        modelo.responder("{\"resposta\":\"O curso custa R$ 1.500.\",\"respondida\":true,\"fontesUsadas\":[\"T9\"]}");

        var resultado = perguntar();

        assertThat(resultado).bodyJson().extractingPath("$.fallback").isEqualTo(true);
        assertThat(resultado).bodyJson().extractingPath("$.resposta").asString().doesNotContain("R$");
    }

    @Test
    void falhaDoMcpGera503SemChamarOModelo() {   // CA10.3
        MCP.comportamento().responderErroDaFerramenta();

        var resultado = perguntar();

        assertErro(resultado, "CONHECIMENTO_INDISPONIVEL");
        assertThat(modelo.recebidos()).isEmpty();
    }

    @Test
    void falhaDoModeloGera503() {   // CA10.5
        MCP.comportamento().responderTrechos(List.of(trecho("Conteúdo.", 0.9)));
        modelo.falhar(new IllegalStateException("500 da OpenAI com detalhe interno"));

        var resultado = perguntar();

        assertErro(resultado, "MODELO_INDISPONIVEL");
        assertThat(resultado).bodyJson().extractingPath("$.detail").asString().doesNotContain("OpenAI", "interno");
    }

    @Test
    void saidaInvalidaDoModeloGera503SemTextoLivre() throws Exception {   // CA10.6
        MCP.comportamento().responderTrechos(List.of(trecho("Conteúdo.", 0.9)));
        modelo.responder("Texto livre inventado: o curso começa amanhã às 19h.");

        var resultado = perguntar();

        assertErro(resultado, "MODELO_INDISPONIVEL");
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("amanhã", "19h");
    }

    @Test
    void logsTemSoMetadadosEOCorrelationId(CapturedOutput saida) {   // T059/T060, CA06.5, RNF02.2
        String marcadorPergunta = "MARCADOR-PERGUNTA-7f3a91";
        String marcadorTrecho = "MARCADOR-TRECHO-0b62de";
        String marcadorResposta = "MARCADOR-RESPOSTA-c4e815";
        MCP.comportamento().responderTrechos(List.of(trecho("Conteúdo " + marcadorTrecho, 0.9)));
        modelo.responder("{\"resposta\":\"" + marcadorResposta + "\",\"respondida\":true,\"fontesUsadas\":[\"T1\"]}");

        var resultado = mvc.post().uri("/api/atendimentos/perguntar")
                .header("X-Correlation-Id", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pergunta\":\"Pergunta " + marcadorPergunta + "?\"}")
                .exchange();

        assertThat(resultado).hasStatusOk();
        assertThat(saida.getAll()).doesNotContain(marcadorPergunta, marcadorTrecho, marcadorResposta, "test-key");
        assertThat(saida.getOut()).contains("Atendimento concluído", "\"correlationId\":\"" + CORRELATION_ID + "\"");
    }

    @Test
    void metricasDoAtendimentoExpostasNoActuator() {   // T058, RNF02.3
        MCP.comportamento().responderTrechos(List.of(trecho("Conteúdo.", 0.9)));
        modelo.responder("{\"resposta\":\"r\",\"respondida\":true,\"fontesUsadas\":[\"T1\",\"T5\"]}");
        perguntar();

        assertThat(mvc.get().uri("/actuator/metrics/coti.atendimento.desfecho?tag=tipo:RESPONDIDA").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/metrics/coti.atendimento.fontes.descartadas").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/metrics/coti.mcp.chamada?tag=resultado:SUCESSO").exchange()).hasStatusOk();
        for (String etapa : List.of("mcp", "modelo", "total")) {
            assertThat(mvc.get().uri("/actuator/metrics/coti.atendimento.etapa?tag=etapa:" + etapa).exchange())
                    .as("timer da etapa %s", etapa).hasStatusOk();
        }
    }

    private MvcTestResult perguntar() {
        return mvc.post().uri("/api/atendimentos/perguntar")
                .header("X-Correlation-Id", CORRELATION_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pergunta\":\"Quais os pré-requisitos do curso Java Web Developer?\"}")
                .exchange();
    }

    private static void assertErro(MvcTestResult resultado, String codigo) {
        assertThat(resultado).hasStatus(503);
        assertThat(resultado.getResponse().getHeader("Retry-After")).isEqualTo("5");
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
        assertThat(resultado).bodyJson().extractingPath("$.correlationId").isEqualTo(CORRELATION_ID);
    }

    private static Map<String, Object> trecho(String texto, double similaridade) {
        Map<String, Object> t = new HashMap<>();
        t.put("chunkId", UUID.randomUUID().toString());
        t.put("texto", texto);
        t.put("documentoId", UUID.randomUUID().toString());
        t.put("titulo", "Curso Java Web Developer");
        t.put("fonte", "https://www.cotiinformatica.com.br/cursos/java-web-developer");
        t.put("categoria", "CURSO");
        t.put("similaridade", similaridade);
        return t;
    }
}

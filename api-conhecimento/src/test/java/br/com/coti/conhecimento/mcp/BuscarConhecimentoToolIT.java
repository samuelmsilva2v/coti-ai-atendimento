package br.com.coti.conhecimento.mcp;

import br.com.coti.conhecimento.application.CadastrarConhecimentoService;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.support.ClienteMcpDeTeste;
import br.com.coti.conhecimento.support.FakeEmbeddingModel;
import br.com.coti.conhecimento.support.McpIntegrationTest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static br.com.coti.conhecimento.support.BaseDeTeste.JAVA;
import static br.com.coti.conhecimento.support.BaseDeTeste.JAVA_PARCIAL;
import static br.com.coti.conhecimento.support.BaseDeTeste.PERGUNTA_JAVA;
import static br.com.coti.conhecimento.support.BaseDeTeste.PERGUNTA_SEM_RELACAO;
import static br.com.coti.conhecimento.support.BaseDeTeste.cadastrar;
import static br.com.coti.conhecimento.support.BaseDeTeste.limpar;
import static org.assertj.core.api.Assertions.assertThat;

/** {@code tools/call buscar_conhecimento} via MCP Streamable HTTP real (CA05.2–CA05.4). */
@McpIntegrationTest
@ExtendWith(OutputCaptureExtension.class)
class BuscarConhecimentoToolIT {

    @LocalServerPort
    private int porta;

    @Autowired
    private CadastrarConhecimentoService cadastro;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FakeEmbeddingModel embeddings;

    private final JsonMapper json = JsonMapper.builder().build();
    private ClienteMcpDeTeste mcp;
    private Conhecimento java;

    @BeforeEach
    void preparar() {
        limpar(jdbc);
        embeddings.reiniciar();
        java = cadastrar(cadastro, "Curso Java Web Developer", JAVA);
        cadastrar(cadastro, "Turma Java Noturna", JAVA_PARCIAL);
        mcp = new ClienteMcpDeTeste(porta);
    }

    @AfterEach
    void encerrar() {
        mcp.close();
        embeddings.reiniciar();
    }

    @Test
    void execucaoComResultadosEstruturados() {   // CA05.2
        CallToolResult resultado = mcp.chamar(Map.of("pergunta", PERGUNTA_JAVA, "limite", 5), null);

        assertThat(resultado.isError()).isFalse();
        JsonNode estruturado = json.valueToTree(resultado.structuredContent());
        assertThat(estruturado.get("total").asInt()).isEqualTo(2);
        JsonNode primeiro = estruturado.get("trechos").get(0);
        assertThat(primeiro.get("documentoId").asString()).isEqualTo(java.id().toString());
        assertThat(primeiro.get("texto").asString()).isEqualTo(JAVA);
        assertThat(primeiro.get("titulo").asString()).isEqualTo("Curso Java Web Developer");
        assertThat(primeiro.get("fonte").asString()).isNotBlank();
        assertThat(primeiro.get("categoria").asString()).isEqualTo("CURSO");
        assertThat(primeiro.get("chunkId").asString()).hasSize(36);
        assertThat(primeiro.get("similaridade").asDouble()).isGreaterThanOrEqualTo(0.38);
        assertThat(primeiro.has("embedding")).isFalse();

        String texto = ((TextContent) resultado.content().getFirst()).text();
        assertThat(json.readTree(texto)).isEqualTo(estruturado);
    }

    @Test
    void semEvidenciaNaoEErro() {   // CA05.3
        CallToolResult resultado = mcp.chamar(Map.of("pergunta", PERGUNTA_SEM_RELACAO), null);

        assertThat(resultado.isError()).isFalse();
        JsonNode estruturado = json.valueToTree(resultado.structuredContent());
        assertThat(estruturado.get("trechos")).isEmpty();
        assertThat(estruturado.get("total").asInt()).isZero();
    }

    @Test
    void argumentosInvalidosNaoGeramEmbedding() {   // CA05.4
        int antes = embeddings.chamadas();

        assertArgumentoInvalido(mcp.chamar(Map.of(), null));
        assertArgumentoInvalido(mcp.chamar(Map.of("pergunta", "   "), null));
        assertArgumentoInvalido(mcp.chamar(Map.of("pergunta", "x".repeat(1001)), null));
        assertArgumentoInvalido(mcp.chamar(Map.of("pergunta", "x", "limite", 0), null));
        assertArgumentoInvalido(mcp.chamar(Map.of("pergunta", "x", "limite", 11), null));
        assertArgumentoInvalido(mcp.chamar(Map.of("pergunta", "x", "limite", "5"), null));
        assertArgumentoInvalido(mcp.chamar(Map.of("pergunta", "x", "outro", true), null));

        assertThat(embeddings.chamadas()).isEqualTo(antes);
    }

    @Test
    void falhaDoProvedorDeEmbeddingsViraErroGenerico() {   // CA05.5 (provedor)
        embeddings.falharAPartirDaChamada(embeddings.chamadas() + 1);

        CallToolResult resultado = mcp.chamar(Map.of("pergunta", PERGUNTA_JAVA), null);

        assertThat(resultado.isError()).isTrue();
        JsonNode erro = json.valueToTree(resultado.structuredContent()).get("erro");
        assertThat(erro.get("codigo").asString()).isEqualTo("EMBEDDING_INDISPONIVEL");
        assertThat(erro.get("mensagem").asString()).doesNotContain("falha simulada", "Exception", "at ");
    }

    @Test
    void correlationIdDoMetaVaiParaOsLogs(CapturedOutput saida) {
        String correlationId = "8d2f9a4e-6b1c-4f3a-9e7d-2c5b8a1f0e34";
        Map<String, Object> meta = new HashMap<>();
        meta.put("correlationId", correlationId);

        mcp.chamar(Map.of("pergunta", PERGUNTA_JAVA), meta);

        assertThat(saida.getOut()).containsPattern(
                "\"correlationId\":\"" + correlationId + "\"[^\\n]*Busca semântica executada"
                        + "|Busca semântica executada[^\\n]*\"correlationId\":\"" + correlationId + "\"");
        assertThat(saida.getOut()).doesNotContain(PERGUNTA_JAVA);
    }

    private void assertArgumentoInvalido(CallToolResult resultado) {
        assertThat(resultado.isError()).isTrue();
        JsonNode erro = json.valueToTree(resultado.structuredContent()).get("erro");
        assertThat(erro.get("codigo").asString()).isEqualTo("ARGUMENTO_INVALIDO");
        assertThat(erro.get("mensagem").asString()).isNotBlank();
    }
}

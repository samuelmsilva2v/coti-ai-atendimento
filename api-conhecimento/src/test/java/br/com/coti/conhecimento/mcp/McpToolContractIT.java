package br.com.coti.conhecimento.mcp;

import br.com.coti.conhecimento.support.ClienteMcpDeTeste;
import br.com.coti.conhecimento.support.McpIntegrationTest;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato da ferramenta (CA05.1): o {@code tools/list} real é comparado com o JSON da seção 4.2
 * lido diretamente de {@code specs/06-api-contracts.md}, a fonte da verdade (Art. 16).
 */
@McpIntegrationTest
class McpToolContractIT {

    private static final Path CONTRATO = Path.of("..", "specs", "06-api-contracts.md");

    @LocalServerPort
    private int porta;

    private final JsonMapper json = JsonMapper.builder().build();
    private ClienteMcpDeTeste mcp;

    @BeforeEach
    void conectar() {
        mcp = new ClienteMcpDeTeste(porta);
    }

    @AfterEach
    void encerrar() {
        mcp.close();
    }

    @Test
    void servidorAnunciaApenasFerramentas() {
        InitializeResult inicio = mcp.sdk().getCurrentInitializationResult();

        assertThat(inicio.serverInfo().name()).isEqualTo("api-conhecimento");
        assertThat(inicio.serverInfo().version()).isEqualTo("1.0.0");
        assertThat(inicio.capabilities().tools()).isNotNull();
        assertThat(inicio.capabilities().resources()).isNull();
        assertThat(inicio.capabilities().prompts()).isNull();
        assertThat(mcp.sdk().listTools().tools()).extracting(Tool::name).containsExactly("buscar_conhecimento");
    }

    @Test
    void definicaoPublicadaIgualAoContrato() throws IOException {
        JsonNode esperado = definicaoDoContrato();
        Tool publicada = mcp.ferramenta("buscar_conhecimento");

        assertThat(publicada.name()).isEqualTo(esperado.get("name").asString());
        assertThat(publicada.title()).isEqualTo(esperado.get("title").asString());
        assertThat(publicada.description()).isEqualTo(esperado.get("description").asString());
        assertThat((JsonNode) json.valueToTree(publicada.inputSchema())).isEqualTo(esperado.get("inputSchema"));
        assertThat((JsonNode) json.valueToTree(publicada.outputSchema())).isEqualTo(esperado.get("outputSchema"));

        JsonNode anotacoes = esperado.get("annotations");
        assertThat(publicada.annotations().readOnlyHint()).isEqualTo(anotacoes.get("readOnlyHint").asBoolean());
        assertThat(publicada.annotations().destructiveHint()).isEqualTo(anotacoes.get("destructiveHint").asBoolean());
        assertThat(publicada.annotations().idempotentHint()).isEqualTo(anotacoes.get("idempotentHint").asBoolean());
        assertThat(publicada.annotations().openWorldHint()).isEqualTo(anotacoes.get("openWorldHint").asBoolean());
    }

    @Test
    void esquemaDeEntradaTemOsLimitesDoContrato() throws IOException {
        JsonNode entrada = definicaoDoContrato().get("inputSchema");

        assertThat(entrada.get("required").get(0).asString()).isEqualTo("pergunta");
        assertThat(entrada.get("additionalProperties").asBoolean()).isFalse();
        assertThat(entrada.at("/properties/pergunta/maxLength").asInt()).isEqualTo(1000);
        assertThat(entrada.at("/properties/limite/maximum").asInt()).isEqualTo(10);
        assertThat(entrada.at("/properties/limite/default").asInt()).isEqualTo(5);
    }

    /** Primeiro bloco ```json depois do título "### 4.2" do contrato. */
    private JsonNode definicaoDoContrato() throws IOException {
        String contrato = Files.readString(CONTRATO);
        String secao = contrato.substring(contrato.indexOf("### 4.2"));
        Matcher bloco = Pattern.compile("```json\\R(.*?)\\R```", Pattern.DOTALL).matcher(secao);
        assertThat(bloco.find()).as("bloco JSON da seção 4.2 do contrato").isTrue();
        return json.readTree(bloco.group(1));
    }
}

package br.com.coti.conhecimento.mcp;

import br.com.coti.conhecimento.application.BuscaSemanticaService;
import br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException;
import br.com.coti.conhecimento.mcp.dto.ErroMcp;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Erros de dependência da ferramenta (CA05.5) sem precisar derrubar o banco real. */
class BuscarConhecimentoToolTest {

    private final BuscaSemanticaService busca = mock(BuscaSemanticaService.class);
    private final BuscarConhecimentoTool ferramenta = new BuscarConhecimentoTool(busca, JsonMapper.builder().build());

    @Test
    void bancoIndisponivelViraErroGenericoSemStackTrace() {
        when(busca.buscar(any())).thenThrow(new DependenciaIndisponivelException(
                DependenciaIndisponivelException.Dependencia.BANCO,
                new DataAccessResourceFailureException("Connection refused: localhost:5434 senha=xyz")));

        assertErro(chamar(), ErroMcp.Codigo.BANCO_INDISPONIVEL);
    }

    @Test
    void falhaDeAcessoADadosNaoEmbrulhadaTambemViraBancoIndisponivel() {
        when(busca.buscar(any())).thenThrow(new DataAccessResourceFailureException("timeout"));

        assertErro(chamar(), ErroMcp.Codigo.BANCO_INDISPONIVEL);
    }

    @Test
    void erroInesperadoViraErroInterno() {
        when(busca.buscar(any())).thenThrow(new IllegalStateException("bug interno com detalhe"));

        assertErro(chamar(), ErroMcp.Codigo.ERRO_INTERNO);
    }

    @Test
    void definicaoCarregadaDoRecursoDoContrato() {
        var tool = ferramenta.especificacao().tool();

        assertThat(tool.name()).isEqualTo("buscar_conhecimento");
        assertThat(tool.inputSchema()).containsKey("properties");
        assertThat(tool.outputSchema()).containsKey("properties");
        assertThat(tool.annotations().readOnlyHint()).isTrue();
    }

    private CallToolResult chamar() {
        return ferramenta.executar(new CallToolRequest("buscar_conhecimento", Map.of("pergunta", "x")));
    }

    private static void assertErro(CallToolResult resultado, ErroMcp.Codigo codigo) {
        assertThat(resultado.isError()).isTrue();
        ErroMcp erro = (ErroMcp) resultado.structuredContent();
        assertThat(erro.erro().codigo()).isEqualTo(codigo);
        String texto = ((TextContent) resultado.content().getFirst()).text();
        assertThat(erro.erro().mensagem() + texto).doesNotContain("senha", "localhost", "Exception", "bug interno");
    }
}

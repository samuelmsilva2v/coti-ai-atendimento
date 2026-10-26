package br.com.coti.conhecimento.mcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

/** Ferramentas publicadas pelo MCP Server (detectadas pela auto-configuração do Spring AI). */
@Configuration(proxyBeanMethods = false)
class McpFerramentasConfig {

    @Bean
    List<SyncToolSpecification> ferramentasMcp(BuscarConhecimentoTool buscarConhecimento) {
        return List.of(buscarConhecimento.especificacao());
    }

    /**
     * A validação genérica do SDK responderia só com texto. Desligada para que toda entrada inválida
     * passe pelas regras da ferramenta (as do inputSchema e mais) e volte com
     * {@code structuredContent.erro.codigo = ARGUMENTO_INVALIDO}, como define o contrato (seção 4.5).
     * A auto-configuração aceita um único customizer, então este delega ao do transporte WebMVC.
     */
    @Bean
    @Primary
    McpSyncServerCustomizer validacaoPelaFerramenta(
            @Qualifier("servletMcpSyncServerCustomizer") McpSyncServerCustomizer doTransporte) {
        return especificacao -> {
            doTransporte.customize(especificacao);
            especificacao.validateToolInputs(false);
        };
    }
}

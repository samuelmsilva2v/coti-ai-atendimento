package br.com.coti.fakemcp;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Autoteste do servidor falso: sobe, publica a ferramenta e responde via MCP real. */
class FakeMcpServerTest {

    @Test
    void publicaAFerramentaPorStreamableHttp() {
        try (var servidor = FakeMcpServer.iniciar()) {
            var cliente = McpClient.sync(HttpClientStreamableHttpTransport.builder(servidor.url()).endpoint("/mcp").build())
                    .requestTimeout(Duration.ofSeconds(5)).build();
            cliente.initialize();

            assertThat(cliente.listTools().tools()).extracting(Tool::name).containsExactly("buscar_conhecimento");
            cliente.closeGracefully();
        }
    }
}

package br.com.coti.conhecimento.support;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;

import java.time.Duration;
import java.util.Map;

/** Cliente MCP Streamable HTTP de verdade (SDK oficial) apontando para a api-conhecimento de teste. */
public final class ClienteMcpDeTeste implements AutoCloseable {

    private final McpSyncClient cliente;

    public ClienteMcpDeTeste(int porta) {
        var transporte = HttpClientStreamableHttpTransport.builder("http://localhost:" + porta)
                .endpoint("/mcp")
                .build();
        this.cliente = McpClient.sync(transporte).requestTimeout(Duration.ofSeconds(10)).build();
        this.cliente.initialize();
    }

    public McpSyncClient sdk() {
        return cliente;
    }

    public Tool ferramenta(String nome) {
        return cliente.listTools().tools().stream().filter(t -> t.name().equals(nome)).findFirst().orElseThrow();
    }

    public CallToolResult chamar(Map<String, Object> argumentos, Map<String, Object> meta) {
        return cliente.callTool(new CallToolRequest("buscar_conhecimento", argumentos, meta));
    }

    @Override
    public void close() {
        cliente.closeGracefully();
    }
}

package br.com.coti.fakemcp;

import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.Map;

/**
 * MCP Server Streamable HTTP de teste, numa aplicação Spring separada e em porta aleatória. Fica fora do
 * pacote {@code br.com.coti.atendimentos} para não ser encontrado pelo scan da aplicação real.
 */
public final class FakeMcpServer implements AutoCloseable {

    private final ConfigurableApplicationContext contexto;
    private final ComportamentoFakeMcp comportamento;

    private FakeMcpServer(ConfigurableApplicationContext contexto, ComportamentoFakeMcp comportamento) {
        this.contexto = contexto;
        this.comportamento = comportamento;
    }

    /** Publica {@code buscar_conhecimento}. */
    public static FakeMcpServer iniciar() {
        return iniciar("buscar_conhecimento");
    }

    /** Publica uma ferramenta com o nome dado (outro nome simula "ferramenta ausente"). */
    public static FakeMcpServer iniciar(String nomeDaFerramenta) {
        var comportamento = new ComportamentoFakeMcp();
        var contexto = new SpringApplicationBuilder(Aplicacao.class)
                .initializers(ctx -> ctx.getBeanFactory().registerSingleton("comportamento", comportamento))
                .properties(
                        "spring.config.name=fake-mcp", // não carrega o application.yml/properties da aplicação real
                        "spring.main.banner-mode=off",
                        // Mesma JVM da aplicação testada: manter o formato ECS, senão o Logback volta ao texto simples sem MDC.
                        "logging.structured.format.console=ecs",
                        "server.port=0",
                        "server.shutdown=immediate", // não esperar o stream SSE aberto pelo cliente ao encerrar
                        "server.address=127.0.0.1",
                        "spring.ai.mcp.client.enabled=false",
                        "spring.ai.model.chat=none", // o servidor falso não usa nenhum modelo da OpenAI
                        "spring.ai.model.embedding=none",
                        "spring.ai.model.audio.speech=none",
                        "spring.ai.model.audio.transcription=none",
                        "spring.ai.model.image=none",
                        "spring.ai.model.moderation=none",
                        "spring.ai.mcp.server.enabled=true",
                        "spring.ai.mcp.server.protocol=STREAMABLE",
                        "spring.ai.mcp.server.name=fake-api-conhecimento",
                        "spring.ai.mcp.server.type=SYNC",
                        "fake.mcp.ferramenta=" + nomeDaFerramenta)
                .run();
        return new FakeMcpServer(contexto, comportamento);
    }

    public String url() {
        return "http://localhost:" + contexto.getEnvironment().getProperty("local.server.port");
    }

    public ComportamentoFakeMcp comportamento() {
        return comportamento;
    }

    @Override
    public void close() {
        contexto.close();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class Aplicacao {

        @Bean
        List<SyncToolSpecification> ferramentas(ComportamentoFakeMcp comportamento,
                org.springframework.core.env.Environment ambiente) {
            Tool ferramenta = Tool.builder()
                    .name(ambiente.getRequiredProperty("fake.mcp.ferramenta"))
                    .description("Ferramenta falsa para testes")
                    .inputSchema(Map.of("type", "object", "properties", Map.of("pergunta", Map.of("type", "string"))))
                    .build();
            return List.of(SyncToolSpecification.builder()
                    .tool(ferramenta)
                    .callHandler((exchange, requisicao) -> comportamento.responder(requisicao))
                    .build());
        }
    }
}

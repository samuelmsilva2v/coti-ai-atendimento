package br.com.coti.atendimentos.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class PropriedadesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Habilitar.class)
            .withPropertyValues("coti.atendimento.mensagem-fallback=Não encontrei.",
                    "coti.rag.top-k=5", "coti.rag.contexto-max-caracteres=12000");

    @Test
    void naoIniciaSemUrlDoMcpServer() {
        runner.run(ctx -> assertThat(ctx).hasFailed()
                .getFailure().rootCause().hasMessageContaining("MCP_CONHECIMENTO_URL"));
        runner.withPropertyValues("coti.atendimento.mcp-conhecimento-url=")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("coti.atendimento.mcp-conhecimento-url=localhost:8081")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void iniciaComConfiguracaoValida() {
        runner.withPropertyValues("coti.atendimento.mcp-conhecimento-url=http://localhost:8081")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void topKForaDaFaixaDoContrato() {
        var comUrl = runner.withPropertyValues("coti.atendimento.mcp-conhecimento-url=http://localhost:8081");

        comUrl.withPropertyValues("coti.rag.top-k=0").run(ctx -> assertThat(ctx).hasFailed());
        comUrl.withPropertyValues("coti.rag.top-k=11").run(ctx -> assertThat(ctx).hasFailed());
        comUrl.withPropertyValues("coti.rag.contexto-max-caracteres=1000").run(ctx -> assertThat(ctx).hasFailed());
    }

    @EnableConfigurationProperties({AtendimentoProperties.class, RagProperties.class})
    static class Habilitar {
    }
}

package br.com.coti.conhecimento.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Habilitar.class);

    @Test
    void sobreposicaoMaiorOuIgualAoTamanhoImpedeAInicializacao() {   // CA02.4
        runner.withPropertyValues("coti.chunking.tamanho-tokens=100", "coti.chunking.sobreposicao-tokens=100")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("CHUNK_OVERLAP deve ser menor que CHUNK_SIZE"));
        runner.withPropertyValues("coti.chunking.tamanho-tokens=100", "coti.chunking.sobreposicao-tokens=150")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void valoresInvalidosImpedemAInicializacao() {
        runner.withPropertyValues("coti.chunking.tamanho-tokens=0", "coti.chunking.sobreposicao-tokens=0")
                .run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("coti.chunking.tamanho-tokens=800", "coti.chunking.sobreposicao-tokens=-1")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void configuracaoPadraoEValida() {
        runner.withPropertyValues("coti.chunking.tamanho-tokens=800", "coti.chunking.sobreposicao-tokens=100")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @EnableConfigurationProperties(ChunkingProperties.class)
    static class Habilitar {
    }
}

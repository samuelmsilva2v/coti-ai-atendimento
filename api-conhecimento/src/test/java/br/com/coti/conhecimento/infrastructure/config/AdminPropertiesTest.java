package br.com.coti.conhecimento.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Habilitar.class);

    @Test
    void naoIniciaSemToken() {
        runner.run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("coti.admin.token=").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void naoIniciaComTokenCurto() {
        runner.withPropertyValues("coti.admin.token=curto").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void iniciaComTokenValidoSemExporOValor() {
        runner.withPropertyValues("coti.admin.token=token-de-teste-0123456789").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(AdminProperties.class).toString()).doesNotContain("token-de-teste");
        });
    }

    @EnableConfigurationProperties(AdminProperties.class)
    static class Habilitar {
    }
}

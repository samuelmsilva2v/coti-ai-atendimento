package br.com.coti.atendimentos.infrastructure.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class McpConhecimentoHealthIndicatorTest {

    private final ConexaoMcpConhecimento conexao = mock(ConexaoMcpConhecimento.class);
    private final RelogioAjustavel relogio = new RelogioAjustavel();
    private final McpConhecimentoHealthIndicator indicador = new McpConhecimentoHealthIndicator(conexao, relogio);

    @Test
    void upQuandoAConexaoResponde() {
        assertThat(indicador.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void downSemDetalhesQuandoAConexaoFalha() {
        doThrow(new IllegalStateException("Connection refused http://localhost:8081")).when(conexao).verificar();

        var saude = indicador.health();

        assertThat(saude.getStatus()).isEqualTo(Status.DOWN);
        assertThat(saude.getDetails()).isEmpty();
    }

    @Test
    void downQuandoPassaDoTimeoutDe2Segundos() {
        var libera = new CountDownLatch(1);
        doAnswer(inv -> {
            libera.await();
            return null;
        }).when(conexao).verificar();
        long inicio = System.nanoTime();

        assertThat(indicador.health().getStatus()).isEqualTo(Status.DOWN);

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(4));
        libera.countDown();
    }

    @Test
    void usaCacheDe10Segundos() {
        indicador.health();
        relogio.avancar(Duration.ofSeconds(9));
        indicador.health();
        verify(conexao, times(1)).verificar();

        relogio.avancar(Duration.ofSeconds(2));
        indicador.health();
        verify(conexao, times(2)).verificar();
    }

    private static final class RelogioAjustavel extends Clock {
        private Instant agora = Instant.parse("2026-10-25T18:30:00Z");

        void avancar(Duration d) {
            agora = agora.plus(d);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}

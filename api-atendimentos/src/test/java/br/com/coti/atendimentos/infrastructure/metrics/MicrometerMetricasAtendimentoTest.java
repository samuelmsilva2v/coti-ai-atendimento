package br.com.coti.atendimentos.infrastructure.metrics;

import br.com.coti.atendimentos.domain.port.MetricasAtendimento.Tipo;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerMetricasAtendimentoTest {

    private final SimpleMeterRegistry registro = new SimpleMeterRegistry();
    private final MicrometerMetricasAtendimento metricas = new MicrometerMetricasAtendimento(registro);

    @Test
    void contaDesfechosPorTipoEMedeOTotal() {
        metricas.registrarAtendimento(Tipo.RESPONDIDA, Duration.ofMillis(120));
        metricas.registrarAtendimento(Tipo.RESPONDIDA, Duration.ofMillis(80));
        metricas.registrarAtendimento(Tipo.CONHECIMENTO_INDISPONIVEL, Duration.ofMillis(10));

        assertThat(registro.get("coti.atendimento.desfecho").tag("tipo", "RESPONDIDA").counter().count()).isEqualTo(2);
        assertThat(registro.get("coti.atendimento.desfecho").tag("tipo", "CONHECIMENTO_INDISPONIVEL").counter().count())
                .isEqualTo(1);
        assertThat(registro.get("coti.atendimento.etapa").tag("etapa", "total").timer().count()).isEqualTo(3);
    }

    @Test
    void contaFontesDescartadasSoQuandoHa() {
        metricas.registrarFontesDescartadas(0);
        assertThat(registro.find("coti.atendimento.fontes.descartadas").counter()).isNull();

        metricas.registrarFontesDescartadas(3);
        assertThat(registro.get("coti.atendimento.fontes.descartadas").counter().count()).isEqualTo(3);
    }
}

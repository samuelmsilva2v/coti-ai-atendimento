package br.com.coti.atendimentos.infrastructure.metrics;

import br.com.coti.atendimentos.domain.port.MetricasAtendimento;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@code coti.atendimento.desfecho{tipo}}, {@code coti.atendimento.fontes.descartadas} e
 * {@code coti.atendimento.etapa{etapa=total}}. As etapas {@code mcp} e {@code modelo} são medidas nos
 * próprios adaptadores, com o mesmo nome de timer.
 */
@Component
public class MicrometerMetricasAtendimento implements MetricasAtendimento {

    public static final String DESFECHO = "coti.atendimento.desfecho";
    public static final String FONTES_DESCARTADAS = "coti.atendimento.fontes.descartadas";
    public static final String ETAPA = "coti.atendimento.etapa";

    private final MeterRegistry registro;

    public MicrometerMetricasAtendimento(MeterRegistry registro) {
        this.registro = registro;
    }

    @Override
    public void registrarAtendimento(Tipo tipo, Duration duracao) {
        Counter.builder(DESFECHO).description("Atendimentos por desfecho").tag("tipo", tipo.name())
                .register(registro).increment();
        etapa(registro, "total").record(duracao);
    }

    @Override
    public void registrarFontesDescartadas(int quantidade) {
        if (quantidade > 0) {
            Counter.builder(FONTES_DESCARTADAS).description("Ids de fonte citados pelo modelo e inexistentes no contexto")
                    .register(registro).increment(quantidade);
        }
    }

    /** Timer por etapa do atendimento: {@code mcp}, {@code modelo} ou {@code total}. */
    public static Timer etapa(MeterRegistry registro, String etapa) {
        return Timer.builder(ETAPA).description("Latência por etapa do atendimento").tag("etapa", etapa)
                .register(registro);
    }
}

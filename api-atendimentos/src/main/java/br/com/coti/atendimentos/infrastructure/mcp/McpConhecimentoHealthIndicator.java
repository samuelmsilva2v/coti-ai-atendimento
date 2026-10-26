package br.com.coti.atendimentos.infrastructure.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Componente {@code mcpConhecimento} do /actuator/health (contrato, seção 5.2): sessão MCP, ferramenta
 * publicada e {@code ping}, com timeout de 2 s. O resultado fica em cache por 10 s para o health check
 * não sobrecarregar a api-conhecimento. Nunca expõe detalhes (URL, erro).
 */
@Component
class McpConhecimentoHealthIndicator implements HealthIndicator {

    static final Duration TIMEOUT = Duration.ofSeconds(2);
    static final Duration CACHE = Duration.ofSeconds(10);

    private static final Logger log = LoggerFactory.getLogger(McpConhecimentoHealthIndicator.class);
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ConexaoMcpConhecimento conexao;
    private final Clock relogio;
    private volatile Instant verificadoEm = Instant.MIN;
    private volatile Health ultimo = Health.unknown().build();

    @Autowired
    McpConhecimentoHealthIndicator(ConexaoMcpConhecimento conexao) {
        this(conexao, Clock.systemUTC());
    }

    /** Relógio injetável só para testar o cache. */
    McpConhecimentoHealthIndicator(ConexaoMcpConhecimento conexao, Clock relogio) {
        this.conexao = conexao;
        this.relogio = relogio;
    }

    @Override
    public Health health() {
        Instant agora = relogio.instant();
        if (agora.isBefore(verificadoEm.plus(CACHE))) {
            return ultimo;
        }
        ultimo = verificar();
        verificadoEm = agora;
        return ultimo;
    }

    private Health verificar() {
        try {
            CompletableFuture.runAsync(conexao::verificar, EXECUTOR).get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return Health.up().build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down().build();
        } catch (Exception e) {
            log.atWarn().addKeyValue("erro", e.getClass().getSimpleName()).log("MCP Server da api-conhecimento indisponível");
            return Health.down().build();
        }
    }
}

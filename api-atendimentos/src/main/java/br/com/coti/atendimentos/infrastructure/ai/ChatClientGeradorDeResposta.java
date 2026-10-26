package br.com.coti.atendimentos.infrastructure.ai;

import br.com.coti.atendimentos.domain.exception.ModeloIndisponivelException;
import br.com.coti.atendimentos.domain.model.ContextoRag;
import br.com.coti.atendimentos.domain.model.SaidaModelo;
import br.com.coti.atendimentos.domain.port.GeradorDeResposta;
import br.com.coti.atendimentos.infrastructure.metrics.MicrometerMetricasAtendimento;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Gera a resposta com o {@link ChatClient} e saída estruturada {@link SaidaModelo} (contrato, seção 6.4).
 * Os trechos e a pergunta entram como parâmetros do template (nunca concatenados no prompt). Qualquer
 * falha do provedor (erro, timeout, tentativas esgotadas) ou de conversão vira
 * {@link ModeloIndisponivelException}; nenhum texto livre do modelo segue adiante nesses casos.
 */
@Component
class ChatClientGeradorDeResposta implements GeradorDeResposta {

    private static final Logger log = LoggerFactory.getLogger(ChatClientGeradorDeResposta.class);

    private final ChatClient chat;
    private final Resource templateUsuario;
    private final Timer etapaModelo;

    ChatClientGeradorDeResposta(ChatClient chat, @Value("classpath:prompts/usuario.st") Resource templateUsuario,
            MeterRegistry metricas) {
        this.chat = chat;
        this.templateUsuario = templateUsuario;
        this.etapaModelo = MicrometerMetricasAtendimento.etapa(metricas, "modelo");
    }

    @Override
    public SaidaModelo gerar(ContextoRag contexto) {
        long inicio = System.nanoTime();
        ResponseEntity<ChatResponse, SaidaModelo> resposta;
        try {
            resposta = chat.prompt()
                    .user(u -> u.text(templateUsuario).params(Map.of(
                            "contexto", contexto.renderizarTrechos(),
                            "pergunta", contexto.perguntaEscapada())))
                    .call()
                    .responseEntity(SaidaModelo.class);
        } catch (RuntimeException e) {
            etapaModelo.record(Duration.ofNanos(System.nanoTime() - inicio));
            log.atWarn().addKeyValue("erro", e.getClass().getSimpleName())
                    .addKeyValue("latenciaModeloMs", (System.nanoTime() - inicio) / 1_000_000)
                    .setCause(e).log("Falha ao gerar a resposta");
            throw new ModeloIndisponivelException(e.getClass().getSimpleName(), e);
        }
        etapaModelo.record(Duration.ofNanos(System.nanoTime() - inicio));
        SaidaModelo saida = resposta.entity();
        if (saida == null) {
            throw new ModeloIndisponivelException("saída estruturada vazia", null);
        }
        registrarUso(resposta.response(), contexto, inicio);
        return saida;
    }

    /** Só metadados: tokens, tamanho do contexto e latência; nunca o prompt nem a resposta (Art. 13). */
    private static void registrarUso(ChatResponse resposta, ContextoRag contexto, long inicio) {
        Usage uso = resposta == null || resposta.getMetadata() == null ? null : resposta.getMetadata().getUsage();
        log.atInfo()
                .addKeyValue("trechosNoContexto", contexto.trechos().size())
                .addKeyValue("tamanhoContexto", contexto.tamanhoEmCaracteres())
                .addKeyValue("tokensPrompt", uso == null ? null : uso.getPromptTokens())
                .addKeyValue("tokensResposta", uso == null ? null : uso.getCompletionTokens())
                .addKeyValue("latenciaModeloMs", (System.nanoTime() - inicio) / 1_000_000)
                .log("Resposta gerada pelo modelo");
    }
}

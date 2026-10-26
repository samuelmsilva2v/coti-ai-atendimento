package br.com.coti.atendimentos.infrastructure.mcp;

import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException;
import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException.Motivo;
import br.com.coti.atendimentos.domain.model.Evidencia;
import br.com.coti.atendimentos.domain.model.Pergunta;
import br.com.coti.atendimentos.domain.port.BuscadorDeConhecimento;
import br.com.coti.atendimentos.infrastructure.metrics.MicrometerMetricasAtendimento;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Chama {@code buscar_conhecimento} de forma determinística, sempre antes de qualquer geração (ADR-005).
 * Qualquer falha vira {@link ConhecimentoIndisponivelException} e nunca uma lista vazia (RN07.4).
 */
@Component
class McpBuscadorDeConhecimento implements BuscadorDeConhecimento {

    private static final Logger log = LoggerFactory.getLogger(McpBuscadorDeConhecimento.class);
    private static final String MDC_CORRELACAO = "correlationId";

    static final String CHAMADA_MCP = "coti.mcp.chamada";

    private final ConexaoMcpConhecimento conexao;
    private final JsonMapper json;
    private final MeterRegistry metricas;

    McpBuscadorDeConhecimento(ConexaoMcpConhecimento conexao, JsonMapper json, MeterRegistry metricas) {
        this.conexao = conexao;
        this.json = json;
        this.metricas = metricas;
    }

    @Override
    public List<Evidencia> buscar(Pergunta pergunta, int limite) {
        long inicio = System.nanoTime();
        try {
            CallToolResult resultado = conexao.chamar(requisicao(pergunta, limite));
            if (Boolean.TRUE.equals(resultado.isError())) {
                throw new ConhecimentoIndisponivelException(Motivo.ERRO_DA_FERRAMENTA, null);
            }
            List<Evidencia> evidencias = converter(resultado);
            medir("SUCESSO", inicio);
            log.atInfo()
                    .addKeyValue("quantidadeEvidencias", evidencias.size())
                    .addKeyValue("latenciaMcpMs", (System.nanoTime() - inicio) / 1_000_000)
                    .log("Busca MCP concluída");
            return evidencias;
        } catch (ConhecimentoIndisponivelException e) {
            throw registrar(e, inicio);
        } catch (RuntimeException e) {
            throw registrar(new ConhecimentoIndisponivelException(timeout(e) ? Motivo.TIMEOUT : Motivo.CONEXAO, e), inicio);
        }
    }

    /** {@code coti.mcp.chamada{resultado}} e {@code coti.atendimento.etapa{etapa=mcp}} (RNF02.3). */
    private void medir(String resultado, long inicio) {
        Counter.builder(CHAMADA_MCP).description("Chamadas à ferramenta buscar_conhecimento por resultado")
                .tag("resultado", resultado).register(metricas).increment();
        MicrometerMetricasAtendimento.etapa(metricas, "mcp").record(Duration.ofNanos(System.nanoTime() - inicio));
    }

    private static CallToolRequest requisicao(Pergunta pergunta, int limite) {
        Map<String, Object> meta = new HashMap<>();
        String correlationId = MDC.get(MDC_CORRELACAO);
        if (correlationId != null) {
            meta.put(MDC_CORRELACAO, correlationId);
        }
        return new CallToolRequest(ConexaoMcpConhecimento.FERRAMENTA,
                Map.of("pergunta", pergunta.texto(), "limite", limite), meta);
    }

    /** Lê {@code structuredContent} ou, na falta dele, o JSON de {@code content[0]} (contrato, seção 4.4). */
    private List<Evidencia> converter(CallToolResult resultado) {
        JsonNode raiz = raiz(resultado);
        JsonNode trechos = raiz == null ? null : raiz.get("trechos");
        if (trechos == null || !trechos.isArray()) {
            throw new ConhecimentoIndisponivelException(Motivo.RESPOSTA_INVALIDA, null);
        }
        List<Evidencia> evidencias = new ArrayList<>();
        int descartados = 0;
        for (JsonNode item : trechos) {
            Evidencia evidencia = evidencia(item);
            if (evidencia == null) {
                descartados++;
            } else {
                evidencias.add(evidencia);
            }
        }
        if (descartados > 0) {
            log.atWarn().addKeyValue("trechosDescartados", descartados).log("Trechos MCP incompletos descartados");
        }
        return evidencias;
    }

    private JsonNode raiz(CallToolResult resultado) {
        if (resultado.structuredContent() != null) {
            return json.valueToTree(resultado.structuredContent());
        }
        if (resultado.content() != null && !resultado.content().isEmpty()
                && resultado.content().getFirst() instanceof TextContent texto) {
            try {
                return json.readTree(texto.text());
            } catch (JacksonException e) {
                throw new ConhecimentoIndisponivelException(Motivo.RESPOSTA_INVALIDA, e);
            }
        }
        return null;
    }

    /** Item sem algum campo obrigatório (ou com UUID inválido) é descartado, não aceito pela metade. */
    private static Evidencia evidencia(JsonNode item) {
        try {
            String chunkId = texto(item, "chunkId");
            String texto = texto(item, "texto");
            String documentoId = texto(item, "documentoId");
            String titulo = texto(item, "titulo");
            String fonte = texto(item, "fonte");
            String categoria = texto(item, "categoria");
            if (chunkId == null || texto == null || texto.isBlank() || documentoId == null || titulo == null
                    || fonte == null || categoria == null) {
                return null;
            }
            JsonNode similaridade = item.get("similaridade");
            return new Evidencia(UUID.fromString(chunkId), texto, UUID.fromString(documentoId), titulo, fonte,
                    categoria, similaridade != null && similaridade.isNumber() ? similaridade.asDouble() : null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String texto(JsonNode item, String campo) {
        JsonNode valor = item.get(campo);
        return valor != null && valor.isString() ? valor.asString() : null;
    }

    private static boolean timeout(Throwable erro) {
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t instanceof TimeoutException || t.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
        }
        return false;
    }

    private ConhecimentoIndisponivelException registrar(ConhecimentoIndisponivelException e, long inicio) {
        medir(e.motivo().name(), inicio);
        log.atWarn()
                .addKeyValue("motivo", e.motivo())
                .addKeyValue("latenciaMcpMs", (System.nanoTime() - inicio) / 1_000_000)
                .setCause(e.getCause())
                .log("Base de conhecimento indisponível");
        return e;
    }
}

package br.com.coti.fakemcp;

import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Respostas programáveis da ferramenta falsa {@code buscar_conhecimento} e registro das chamadas recebidas. */
public class ComportamentoFakeMcp {

    private final List<CallToolRequest> recebidas = new CopyOnWriteArrayList<>();
    private volatile Function<CallToolRequest, CallToolResult> resposta = r -> estruturado(Map.of("trechos", List.of(), "total", 0));

    CallToolResult responder(CallToolRequest requisicao) {
        recebidas.add(requisicao);
        return resposta.apply(requisicao);
    }

    public List<CallToolRequest> recebidas() {
        return recebidas;
    }

    public void reiniciar() {
        recebidas.clear();
        responderVazio();
    }

    public void responderTrechos(List<Map<String, Object>> trechos) {
        resposta = r -> estruturado(Map.of("trechos", trechos, "total", trechos.size()));
    }

    public void responderVazio() {
        responderTrechos(List.of());
    }

    /** Sem {@code structuredContent}: só o JSON em {@code content[0]} (clientes antigos; fallback do cliente). */
    public void responderSoComTexto(String json) {
        resposta = r -> CallToolResult.builder().addTextContent(json).isError(false).build();
    }

    public void responderErroDaFerramenta() {
        resposta = r -> CallToolResult.builder()
                .structuredContent(Map.of("erro", Map.of("codigo", "BANCO_INDISPONIVEL", "mensagem", "indisponível")))
                .addTextContent("indisponível").isError(true).build();
    }

    public void responderForaDoEsquema() {
        resposta = r -> estruturado(Map.of("resultado", "inesperado"));
    }

    /** Atraso do lado do servidor, para exercitar o timeout do cliente sem {@code sleep} no teste. */
    public void atrasar(Duration atraso) {
        resposta = r -> {
            try {
                Thread.sleep(atraso);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return estruturado(Map.of("trechos", List.of(), "total", 0));
        };
    }

    private static CallToolResult estruturado(Object conteudo) {
        return CallToolResult.builder().structuredContent(conteudo).addTextContent("{}").isError(false).build();
    }
}

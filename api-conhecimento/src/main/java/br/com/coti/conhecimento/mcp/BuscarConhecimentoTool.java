package br.com.coti.conhecimento.mcp;

import br.com.coti.conhecimento.application.BuscaSemanticaService;
import br.com.coti.conhecimento.domain.exception.ConsultaInvalidaException;
import br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException;
import br.com.coti.conhecimento.domain.model.ConsultaBusca;
import br.com.coti.conhecimento.mcp.dto.ErroMcp;
import br.com.coti.conhecimento.mcp.dto.ResultadoBuscaMcp;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import io.modelcontextprotocol.spec.McpSchema.ToolAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Ferramenta MCP {@code buscar_conhecimento} (RF05). Registrada programaticamente, e não com
 * {@code @McpTool}, para publicar exatamente o inputSchema/outputSchema do contrato (limites e
 * default) e devolver {@code isError} com {@code structuredContent.erro} (03-architecture, seção 5.5).
 * Somente leitura e sem efeitos colaterais.
 */
@Component
public class BuscarConhecimentoTool {

    public static final String NOME = "buscar_conhecimento";
    private static final String DEFINICAO = "mcp/buscar_conhecimento.json";
    private static final Set<String> ARGUMENTOS = Set.of("pergunta", "limite");
    private static final Pattern UUID_CANONICO =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final String MDC_CORRELACAO = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(BuscarConhecimentoTool.class);

    private final BuscaSemanticaService busca;
    private final JsonMapper json;
    private final Tool definicao;

    public BuscarConhecimentoTool(BuscaSemanticaService busca, JsonMapper json) {
        this.busca = busca;
        this.json = json;
        this.definicao = carregarDefinicao(json);
    }

    public SyncToolSpecification especificacao() {
        return SyncToolSpecification.builder().tool(definicao).callHandler((exchange, req) -> executar(req)).build();
    }

    CallToolResult executar(CallToolRequest requisicao) {
        String anterior = MDC.get(MDC_CORRELACAO);
        MDC.put(MDC_CORRELACAO, correlationId(requisicao.meta()));
        try {
            ConsultaBusca consulta = consulta(requisicao.arguments());
            return sucesso(ResultadoBuscaMcp.de(busca.buscar(consulta)));
        } catch (ConsultaInvalidaException e) {
            return erro(ErroMcp.Codigo.ARGUMENTO_INVALIDO, e.getMessage(), null);
        } catch (DependenciaIndisponivelException e) {
            return e.dependencia() == DependenciaIndisponivelException.Dependencia.EMBEDDING
                    ? erro(ErroMcp.Codigo.EMBEDDING_INDISPONIVEL, "Serviço de embeddings indisponível no momento.", e)
                    : erro(ErroMcp.Codigo.BANCO_INDISPONIVEL, "Base de conhecimento indisponível no momento.", e);
        } catch (DataAccessException e) {
            return erro(ErroMcp.Codigo.BANCO_INDISPONIVEL, "Base de conhecimento indisponível no momento.", e);
        } catch (RuntimeException e) {
            return erro(ErroMcp.Codigo.ERRO_INTERNO, "Erro inesperado ao executar a busca.", e);
        } finally {
            if (anterior != null) {
                MDC.put(MDC_CORRELACAO, anterior);
            } else {
                MDC.remove(MDC_CORRELACAO);
            }
        }
    }

    private static ConsultaBusca consulta(Map<String, Object> argumentos) {
        Map<String, Object> args = argumentos == null ? Map.of() : argumentos;
        for (String nome : args.keySet()) {
            if (!ARGUMENTOS.contains(nome)) {
                throw new ConsultaInvalidaException(nome, "Argumento desconhecido: '" + nome + "'.");
            }
        }
        Object pergunta = args.get("pergunta");
        if (pergunta != null && !(pergunta instanceof String)) {
            throw new ConsultaInvalidaException("pergunta", "O argumento 'pergunta' deve ser texto.");
        }
        return ConsultaBusca.de((String) pergunta, inteiro(args.get("limite")));
    }

    private static Integer inteiro(Object valor) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())
                && Math.abs(n.doubleValue()) <= Integer.MAX_VALUE) {
            return n.intValue();
        }
        throw new ConsultaInvalidaException("limite", "O argumento 'limite' deve ser um número inteiro.");
    }

    private CallToolResult sucesso(ResultadoBuscaMcp resultado) {
        return CallToolResult.builder()
                .structuredContent(resultado)
                .addTextContent(json.writeValueAsString(resultado))
                .isError(false)
                .build();
    }

    private CallToolResult erro(ErroMcp.Codigo codigo, String mensagem, Exception causa) {
        if (causa != null) {
            log.warn("Ferramenta {} falhou: {}", NOME, codigo, causa); // detalhe técnico só no log
        } else {
            log.atInfo().addKeyValue("codigo", codigo).log("Ferramenta " + NOME + " rejeitou os argumentos");
        }
        return CallToolResult.builder()
                .structuredContent(ErroMcp.de(codigo, mensagem))
                .addTextContent(mensagem)
                .isError(true)
                .build();
    }

    private static String correlationId(Map<String, Object> meta) {
        Object recebido = meta == null ? null : meta.get(MDC_CORRELACAO);
        if (recebido instanceof String s && UUID_CANONICO.matcher(s).matches()) {
            return s.toLowerCase();
        }
        return UUID.randomUUID().toString();
    }

    private static Tool carregarDefinicao(JsonMapper json) {
        try (InputStream entrada = new ClassPathResource(DEFINICAO).getInputStream()) {
            Map<String, Object> d = json.readValue(entrada, new TypeReference<>() { });
            @SuppressWarnings("unchecked")
            Map<String, Object> anotacoes = (Map<String, Object>) d.get("annotations");
            return Tool.builder()
                    .name((String) d.get("name"))
                    .title((String) d.get("title"))
                    .description((String) d.get("description"))
                    .inputSchema(mapa(d.get("inputSchema")))
                    .outputSchema(mapa(d.get("outputSchema")))
                    .annotations(new ToolAnnotations(null,
                            (Boolean) anotacoes.get("readOnlyHint"),
                            (Boolean) anotacoes.get("destructiveHint"),
                            (Boolean) anotacoes.get("idempotentHint"),
                            (Boolean) anotacoes.get("openWorldHint"),
                            null))
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException("Definição da ferramenta MCP ausente: " + DEFINICAO, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapa(Object valor) {
        return (Map<String, Object>) valor;
    }
}

package br.com.coti.atendimentos.infrastructure.mcp;

import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException;
import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException.Motivo;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Conexão MCP (Streamable HTTP) com a api-conhecimento. Com {@code initialized: false}, a sessão é
 * aberta de forma preguiçosa no primeiro uso, e a existência de {@code buscar_conhecimento} é
 * confirmada uma vez via {@code tools/list}. Usada pelo buscador e pelo health indicator.
 */
@Component
public class ConexaoMcpConhecimento {

    public static final String FERRAMENTA = "buscar_conhecimento";

    private final McpSyncClient cliente;
    private final ReentrantLock trava = new ReentrantLock(); // não fixa virtual threads, ao contrário de synchronized
    private volatile boolean ferramentaConfirmada;

    public ConexaoMcpConhecimento(List<McpSyncClient> clientes) {
        if (clientes.size() != 1) {
            throw new IllegalStateException("Esperada exatamente uma conexão MCP (conhecimento); encontradas: "
                    + clientes.size());
        }
        this.cliente = clientes.getFirst();
    }

    public CallToolResult chamar(CallToolRequest requisicao) {
        garantirPronta();
        return cliente.callTool(requisicao);
    }

    /** Para o health check: sessão aberta, ferramenta publicada e servidor respondendo a {@code ping}. */
    public void verificar() {
        garantirPronta();
        cliente.ping();
    }

    private void garantirPronta() {
        if (cliente.isInitialized() && ferramentaConfirmada) {
            return;
        }
        trava.lock();
        try {
            if (!cliente.isInitialized()) {
                cliente.initialize();
            }
            if (!ferramentaConfirmada) {
                boolean publicada = cliente.listTools().tools().stream().anyMatch(t -> FERRAMENTA.equals(t.name()));
                if (!publicada) {
                    throw new ConhecimentoIndisponivelException(Motivo.FERRAMENTA_AUSENTE, null);
                }
                ferramentaConfirmada = true;
            }
        } finally {
            trava.unlock();
        }
    }
}

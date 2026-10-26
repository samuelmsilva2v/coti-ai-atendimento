package br.com.coti.atendimentos.infrastructure.mcp;

import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException;
import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException.Motivo;
import br.com.coti.atendimentos.domain.model.Pergunta;
import br.com.coti.atendimentos.domain.port.BuscadorDeConhecimento;
import br.com.coti.fakemcp.FakeMcpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Servidor que não publica {@code buscar_conhecimento}: falha de contrato tratada como indisponibilidade. */
@SpringBootTest
class McpFerramentaAusenteIT {

    private static final FakeMcpServer SERVIDOR = FakeMcpServer.iniciar("outra_ferramenta");

    @DynamicPropertySource
    static void apontar(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.ai.mcp.client.streamable-http.connections.conhecimento.url", SERVIDOR::url);
    }

    @AfterAll
    static void parar() {
        SERVIDOR.close();
    }

    @Autowired
    private BuscadorDeConhecimento buscador;

    @Test
    void ferramentaNaoPublicadaGeraIndisponivelSemChamar() {
        assertThatThrownBy(() -> buscador.buscar(new Pergunta("x"), 5))
                .isInstanceOf(ConhecimentoIndisponivelException.class)
                .satisfies(e -> assertThat(((ConhecimentoIndisponivelException) e).motivo())
                        .isEqualTo(Motivo.FERRAMENTA_AUSENTE));
        assertThat(SERVIDOR.comportamento().recebidas()).isEmpty();
    }
}

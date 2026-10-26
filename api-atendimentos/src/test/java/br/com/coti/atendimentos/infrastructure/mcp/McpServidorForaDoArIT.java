package br.com.coti.atendimentos.infrastructure.mcp;

import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException;
import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException.Motivo;
import br.com.coti.atendimentos.domain.model.Pergunta;
import br.com.coti.atendimentos.domain.port.BuscadorDeConhecimento;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O application.properties de teste aponta para a porta 1, onde nunca há servidor (CA10.3). */
@SpringBootTest
class McpServidorForaDoArIT {

    @Autowired
    private BuscadorDeConhecimento buscador;

    @Test
    void servidorForaDoArGeraIndisponivel() {
        assertThatThrownBy(() -> buscador.buscar(new Pergunta("x"), 5))
                .isInstanceOf(ConhecimentoIndisponivelException.class)
                .satisfies(e -> assertThat(((ConhecimentoIndisponivelException) e).motivo())
                        .isIn(Motivo.CONEXAO, Motivo.TIMEOUT));
    }
}

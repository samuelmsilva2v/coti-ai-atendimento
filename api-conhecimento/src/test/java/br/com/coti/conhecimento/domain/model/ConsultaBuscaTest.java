package br.com.coti.conhecimento.domain.model;

import br.com.coti.conhecimento.domain.exception.ConsultaInvalidaException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsultaBuscaTest {

    @Test
    void limitePadraoEPerguntaNormalizada() {
        var consulta = ConsultaBusca.de("  Quais os pré-requisitos?  ", null);

        assertThat(consulta.pergunta()).isEqualTo("Quais os pré-requisitos?");
        assertThat(consulta.limite()).isEqualTo(5);
    }

    @Test
    void limitesValidosNasBordas() {
        assertThat(ConsultaBusca.de("p", 1).limite()).isEqualTo(1);
        assertThat(ConsultaBusca.de("p".repeat(1000), 10).limite()).isEqualTo(10);
    }

    @Test
    void perguntaAusenteOuEmBranco() {
        assertInvalida(null, null, "pergunta");
        assertInvalida("   ", null, "pergunta");
    }

    @Test
    void perguntaLongaDemaisAposTrim() {
        assertInvalida("p".repeat(1001), null, "pergunta");
        assertThat(ConsultaBusca.de("  " + "p".repeat(1000) + "  ", null).pergunta()).hasSize(1000);
    }

    @Test
    void limiteForaDaFaixa() {
        assertInvalida("p", 0, "limite");
        assertInvalida("p", 11, "limite");
        assertThatThrownBy(() -> ConsultaBusca.de("p", 50))
                .hasMessage("O argumento 'limite' deve estar entre 1 e 10.");
    }

    private static void assertInvalida(String pergunta, Integer limite, String campo) {
        assertThatThrownBy(() -> ConsultaBusca.de(pergunta, limite))
                .isInstanceOf(ConsultaInvalidaException.class)
                .satisfies(e -> assertThat(((ConsultaInvalidaException) e).campo()).isEqualTo(campo));
    }
}

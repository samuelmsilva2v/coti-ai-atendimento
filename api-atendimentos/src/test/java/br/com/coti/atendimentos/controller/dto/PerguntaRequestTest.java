package br.com.coti.atendimentos.controller.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PerguntaRequestTest {

    private static ValidatorFactory fabrica;

    @BeforeAll
    static void criar() {
        fabrica = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void fechar() {
        fabrica.close();
    }

    @Test
    void perguntaValidaNoLimite() {
        assertThat(validar("p".repeat(1000))).isEmpty();
    }

    @Test
    void perguntaAusenteOuEmBrancoEInvalida() {   // CA06.2
        assertThat(validar(null)).hasSize(1);
        assertThat(validar("   ")).hasSize(1);
    }

    @Test
    void perguntaLongaDemaisEInvalida() {   // CA06.3
        assertThat(validar("p".repeat(1001))).hasSize(1);
    }

    @Test
    void dominioRecebeAPerguntaSemEspacosNasPontasEToStringNaoExpoeOTexto() {
        var pergunta = new PerguntaRequest("  Quanto custa o curso?  ").paraDominio();

        assertThat(pergunta.texto()).isEqualTo("Quanto custa o curso?");
        assertThat(pergunta.toString()).doesNotContain("custa");
    }

    private static Set<ConstraintViolation<PerguntaRequest>> validar(String pergunta) {
        return fabrica.getValidator().validate(new PerguntaRequest(pergunta));
    }
}

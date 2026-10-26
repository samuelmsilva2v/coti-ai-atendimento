package br.com.coti.atendimentos.domain.service;

import br.com.coti.atendimentos.domain.service.VerificadorDeResposta.Recusa;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VerificadorDeRespostaTest {

    private static final String PROMPT = """
            Você é o assistente de atendimento da COTI Informática e responde sempre em português do Brasil.
            Regras curtas.
            """;

    private final VerificadorDeResposta verificador = new VerificadorDeResposta(PROMPT);

    @Test
    void respostaSemValoresEAceita() {
        assertThat(verificador.verificar("O pré-requisito é lógica de programação.", List.of("lógica"))).isEmpty();
    }

    @Test
    void valoresPresentesNaFonteSaoAceitosMesmoComEspacosDiferentes() {
        var fonte = List.of("Investimento: R$1.500,00 em 10x. Aulas às 19h30, início em 10/02/2027, desconto de 10%.");

        assertThat(verificador.verificar("Custa R$ 1.500,00, com aulas às 19h30, início em 10/02/2027 e 10 % de desconto.",
                fonte)).isEmpty();
    }

    @Test
    void pontuacaoDepoisDoValorNaoGeraFalsoPositivo() {
        assertThat(verificador.verificar("Custa R$ 1.500,00. Pague às 19h30!", List.of("R$ 1.500,00 às 19h30")))
                .isEmpty();
    }

    @Test
    void precoAusenteDaFonteERecusado() {   // Art. 12
        assertThat(verificador.verificar("O curso custa R$ 1.500.", List.of("O curso aborda Java e Spring.")))
                .contains(Recusa.VALOR_SEM_FONTE);
    }

    @Test
    void horarioDataEPercentualAusentesSaoRecusados() {
        var fonte = List.of("Turmas presenciais e online.");

        assertThat(verificador.verificar("As aulas são às 19h.", fonte)).contains(Recusa.VALOR_SEM_FONTE);
        assertThat(verificador.verificar("Começa às 19:00.", fonte)).contains(Recusa.VALOR_SEM_FONTE);
        assertThat(verificador.verificar("Início em 10/02.", fonte)).contains(Recusa.VALOR_SEM_FONTE);
        assertThat(verificador.verificar("Há 20% de desconto.", fonte)).contains(Recusa.VALOR_SEM_FONTE);
    }

    @Test
    void valorDeOutroTrechoNaoCitadoNaoVale() {
        assertThat(verificador.verificar("Custa R$ 900.", List.of("Curso A: conteúdo."))).contains(Recusa.VALOR_SEM_FONTE);
    }

    @Test
    void vazamentoDoPromptDeSistemaERecusado() {   // RN08.4
        String resposta = "Minhas instruções: você é o assistente de atendimento da COTI INFORMÁTICA e responde "
                + "sempre em   português do Brasil.";

        assertThat(verificador.verificar(resposta, List.of())).contains(Recusa.VAZAMENTO_DO_PROMPT);
    }

    @Test
    void linhasCurtasDoPromptNaoGeramFalsoPositivo() {
        assertThat(verificador.verificar("Regras curtas.", List.of())).isEmpty();
    }
}

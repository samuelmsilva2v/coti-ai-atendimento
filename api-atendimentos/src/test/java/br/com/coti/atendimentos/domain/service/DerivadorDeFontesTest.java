package br.com.coti.atendimentos.domain.service;

import br.com.coti.atendimentos.domain.model.ContextoRag;
import br.com.coti.atendimentos.domain.model.Evidencia;
import br.com.coti.atendimentos.domain.model.Fonte;
import br.com.coti.atendimentos.domain.model.Pergunta;
import br.com.coti.atendimentos.domain.model.SaidaModelo;
import br.com.coti.atendimentos.domain.service.DerivadorDeFontes.Derivacao;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class DerivadorDeFontesTest {

    private final DerivadorDeFontes derivador = new DerivadorDeFontes();
    private final List<Evidencia> evidencias = IntStream.rangeClosed(1, 5)
            .mapToObj(i -> new Evidencia(UUID.randomUUID(), "texto " + i, UUID.randomUUID(), "Título " + i,
                    "https://coti/" + i, "CURSO", 0.9 - i / 100.0))
            .toList();
    private final ContextoRag contexto = new MontadorDeContexto(12_000).montar(new Pergunta("p"), evidencias);

    @Test
    void fontesVemSoDasEvidenciasNaOrdemDoContexto() {   // CA09.1 e CA09.2
        Derivacao d = derivador.derivar(new SaidaModelo("resposta", true, List.of("T4", "T2", "T2")), contexto);

        assertThat(d.fallback()).isFalse();
        assertThat(d.fontes()).containsExactly(Fonte.de(evidencias.get(1)), Fonte.de(evidencias.get(3)));
        assertThat(d.fontesDescartadas()).isZero();
    }

    @Test
    void idsInexistentesSaoDescartadosEContados() {   // CA08.3
        Derivacao d = derivador.derivar(new SaidaModelo("resposta", true,
                List.of("T1", "T9", "chunk-inventado", UUID.randomUUID().toString())), contexto);

        assertThat(d.fontes()).containsExactly(Fonte.de(evidencias.getFirst()));
        assertThat(d.fontesDescartadas()).isEqualTo(3);
    }

    @Test
    void semFonteValidaViraFallback() {   // CA08.3
        Derivacao d = derivador.derivar(new SaidaModelo("O curso custa R$ 100.", true, List.of("T99")), contexto);

        assertThat(d.fallback()).isTrue();
        assertThat(d.fontes()).isEmpty();
    }

    @Test
    void modeloDeclaraQueNaoSabeViraFallback() {   // CA08.4
        assertThat(derivador.derivar(new SaidaModelo("não sei", false, List.of("T1")), contexto).fallback()).isTrue();
    }

    @Test
    void respostaVaziaOuSemFontesViraFallback() {
        assertThat(derivador.derivar(new SaidaModelo("  ", true, List.of("T1")), contexto).fallback()).isTrue();
        assertThat(derivador.derivar(new SaidaModelo("resposta", true, null), contexto).fallback()).isTrue();
    }
}

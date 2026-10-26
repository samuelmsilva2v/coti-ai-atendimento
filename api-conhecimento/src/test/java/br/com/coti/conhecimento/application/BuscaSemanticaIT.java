package br.com.coti.conhecimento.application;

import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.ConsultaBusca;
import br.com.coti.conhecimento.domain.model.ResultadoBusca;
import br.com.coti.conhecimento.domain.model.Trecho;
import br.com.coti.conhecimento.support.FakeEmbeddingModel;
import br.com.coti.conhecimento.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static br.com.coti.conhecimento.support.BaseDeTeste.JAVA;
import static br.com.coti.conhecimento.support.BaseDeTeste.JAVA_PARCIAL;
import static br.com.coti.conhecimento.support.BaseDeTeste.NUTRICAO;
import static br.com.coti.conhecimento.support.BaseDeTeste.PERGUNTA_JAVA;
import static br.com.coti.conhecimento.support.BaseDeTeste.PERGUNTA_SEM_RELACAO;
import static br.com.coti.conhecimento.support.BaseDeTeste.cadastrar;
import static br.com.coti.conhecimento.support.BaseDeTeste.limpar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Busca vetorial real no pgvector (cosseno + limiar padrão 0,38) com embeddings determinísticos. */
@IntegrationTest
class BuscaSemanticaIT {

    @Autowired
    private BuscaSemanticaService busca;

    @Autowired
    private CadastrarConhecimentoService cadastro;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FakeEmbeddingModel embeddings;

    private Conhecimento java;
    private Conhecimento javaParcial;

    @BeforeEach
    void preparar() {
        limpar(jdbc);
        embeddings.reiniciar();
        java = cadastrar(cadastro, "Curso Java Web Developer", JAVA);
        javaParcial = cadastrar(cadastro, "Turma Java Noturna", JAVA_PARCIAL);
        cadastrar(cadastro, "Receita de Bolo", NUTRICAO);
    }

    @Test
    void trechosRelevantesComMetadadosESimilaridade() {   // CA04.1
        ResultadoBusca resultado = busca.buscar(ConsultaBusca.de(PERGUNTA_JAVA, 5));

        Trecho primeiro = resultado.trechos().getFirst();
        assertThat(primeiro.documentoId()).isEqualTo(java.id());
        assertThat(primeiro.texto()).isEqualTo(JAVA);
        assertThat(primeiro.titulo()).isEqualTo("Curso Java Web Developer");
        assertThat(primeiro.fonte()).isEqualTo("https://www.cotiinformatica.com.br/curso-java-web-developer");
        assertThat(primeiro.categoria()).isNotNull();
        assertThat(primeiro.chunkId()).isNotNull();
        assertThat(resultado.limiarSimilaridade()).isEqualTo(0.38);
    }

    @Test
    void similaridadeEUmMenosADistanciaDeCosseno() {
        ResultadoBusca resultado = busca.buscar(ConsultaBusca.de(PERGUNTA_JAVA, 5));

        assertThat(resultado.trechos()).hasSize(2);
        assertThat(resultado.trechos().get(0).similaridade()).isCloseTo(1.0, within(0.001));
        assertThat(resultado.trechos().get(1).documentoId()).isEqualTo(javaParcial.id());
        assertThat(resultado.trechos().get(1).similaridade()).isCloseTo(0.75, within(0.02));
    }

    @Test
    void respeitaOLimiteEAOrdemDecrescente() {   // CA04.2
        assertThat(busca.buscar(ConsultaBusca.de(PERGUNTA_JAVA, 1)).trechos())
                .singleElement()
                .extracting(Trecho::documentoId).isEqualTo(java.id());
        assertThat(busca.buscar(ConsultaBusca.de(PERGUNTA_JAVA, 5)).trechos())
                .extracting(Trecho::similaridade)
                .isSortedAccordingTo((a, b) -> Double.compare(b, a));
    }

    @Test
    void perguntaSemRelacaoRetornaListaVazia() {   // CA04.3
        assertThat(busca.buscar(ConsultaBusca.de(PERGUNTA_SEM_RELACAO, 10)).trechos()).isEmpty();
    }

    @Test
    void nadaAbaixoDoLimiarEDevolvido() {   // RN04.1
        assertThat(busca.buscar(ConsultaBusca.de(PERGUNTA_JAVA, 10)).trechos())
                .allSatisfy(t -> assertThat(t.similaridade()).isGreaterThanOrEqualTo(0.38))
                .noneMatch(t -> t.texto().equals(NUTRICAO));
    }

    @Test
    void ingestaoEBuscaUsamOMesmoModeloDeEmbedding() {   // CA03.3
        int antes = embeddings.chamadas();

        busca.buscar(ConsultaBusca.de(PERGUNTA_JAVA, 1));

        assertThat(embeddings.chamadas()).isGreaterThan(antes);
    }
}

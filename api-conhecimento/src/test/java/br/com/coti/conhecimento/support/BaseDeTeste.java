package br.com.coti.conhecimento.support;

import br.com.coti.conhecimento.application.CadastrarConhecimentoService;
import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.NovoConhecimento;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

/**
 * Documentos de 12 palavras distintas: com o {@link FakeEmbeddingModel}, a similaridade de cosseno é
 * (palavras em comum) / 12. A pergunta {@link #PERGUNTA_JAVA} é idêntica a {@link #JAVA} (1,0) e
 * compartilha 9 palavras com {@link #JAVA_PARCIAL} (0,75); {@link #NUTRICAO} não tem nenhuma em comum.
 */
public final class BaseDeTeste {

    public static final String JAVA =
            "matricula curso java web developer turma noite modalidade presencial sala alfa beta";
    public static final String JAVA_PARCIAL =
            "matricula curso java web developer turma noite modalidade presencial gama delta epsilon";
    public static final String NUTRICAO =
            "receita bolo cenoura farinha acucar ovos forno chocolate cobertura leite manteiga fermento";
    public static final String PERGUNTA_JAVA = JAVA;
    public static final String PERGUNTA_SEM_RELACAO = "qual capital australia oceania canguru";

    private BaseDeTeste() {
    }

    public static void limpar(JdbcClient jdbc) {
        jdbc.sql("TRUNCATE conhecimento, vector_store").update();
    }

    public static Conhecimento cadastrar(CadastrarConhecimentoService servico, String titulo, String conteudo) {
        return servico.cadastrar(new NovoConhecimento(titulo, conteudo, Categoria.CURSO,
                "https://www.cotiinformatica.com.br/" + titulo.toLowerCase().replace(' ', '-'), List.of()));
    }
}

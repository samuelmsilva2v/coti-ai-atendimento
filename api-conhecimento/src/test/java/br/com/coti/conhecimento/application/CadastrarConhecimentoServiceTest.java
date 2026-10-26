package br.com.coti.conhecimento.application;

import br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException;
import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Chunk;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.NovoConhecimento;
import br.com.coti.conhecimento.domain.port.ChunkStore;
import br.com.coti.conhecimento.domain.port.Chunker;
import br.com.coti.conhecimento.domain.port.ConhecimentoRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CadastrarConhecimentoServiceTest {

    private static final List<Chunk> DOIS_CHUNKS = List.of(new Chunk("a", 0, 2, 0), new Chunk("b", 1, 2, 1));

    private final ConhecimentoRepository repositorio = mock(ConhecimentoRepository.class);
    private final Chunker chunker = mock(Chunker.class);
    private final ChunkStore chunkStore = mock(ChunkStore.class);
    private final CadastrarConhecimentoService servico = new CadastrarConhecimentoService(repositorio, chunker, chunkStore);

    @Test
    void normalizaDivideEPersisteDocumentoEChunks() {
        when(chunker.dividir(anyString())).thenReturn(DOIS_CHUNKS);
        var novo = new NovoConhecimento("  Curso Java  ", "  conteúdo preservado  ", Categoria.CURSO,
                " https://coti ", List.of(" java ", "spring"));

        Conhecimento salvo = servico.cadastrar(novo);

        verify(chunker).dividir("  conteúdo preservado  ");
        var captor = ArgumentCaptor.forClass(Conhecimento.class);
        InOrder ordem = inOrder(repositorio, chunkStore);
        ordem.verify(repositorio).inserir(captor.capture());
        ordem.verify(chunkStore).adicionar(salvo, DOIS_CHUNKS);
        assertThat(captor.getValue()).isEqualTo(salvo);
        assertThat(salvo.titulo()).isEqualTo("Curso Java");
        assertThat(salvo.fonte()).isEqualTo("https://coti");
        assertThat(salvo.conteudo()).isEqualTo("  conteúdo preservado  ");
        assertThat(salvo.tags()).containsExactly("java", "spring");
        assertThat(salvo.quantidadeChunks()).isEqualTo(2);
        assertThat(salvo.tamanhoConteudo()).isEqualTo(novo.conteudo().length());
        assertThat(salvo.criadoEm()).isEqualTo(salvo.criadoEm().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void tagsAusentesViramListaVazia() {
        when(chunker.dividir(anyString())).thenReturn(DOIS_CHUNKS);

        var salvo = servico.cadastrar(new NovoConhecimento("T", "C", Categoria.INSTITUCIONAL, "F", null));

        assertThat(salvo.tags()).isEmpty();
    }

    @Test
    void falhaDosEmbeddingsPropagaParaDesfazerATransacao() {
        when(chunker.dividir(anyString())).thenReturn(DOIS_CHUNKS);
        var falha = new DependenciaIndisponivelException(DependenciaIndisponivelException.Dependencia.EMBEDDING,
                new RuntimeException());
        doThrow(falha).when(chunkStore).adicionar(any(), anyList());

        assertThatThrownBy(() -> servico.cadastrar(new NovoConhecimento("T", "C", Categoria.CURSO, "F", null)))
                .isSameAs(falha);
    }
}

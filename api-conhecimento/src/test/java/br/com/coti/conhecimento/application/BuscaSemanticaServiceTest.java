package br.com.coti.conhecimento.application;

import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.ConsultaBusca;
import br.com.coti.conhecimento.domain.model.ResultadoBusca;
import br.com.coti.conhecimento.domain.model.Trecho;
import br.com.coti.conhecimento.domain.port.ChunkStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuscaSemanticaServiceTest {

    private final ChunkStore chunkStore = mock(ChunkStore.class);
    private final BuscaSemanticaService servico = new BuscaSemanticaService(chunkStore);

    @Test
    void ordenaPorSimilaridadeDecrescenteComNulosNoFim() {
        var consulta = ConsultaBusca.de("pergunta", 3);
        when(chunkStore.buscar(consulta)).thenReturn(new ResultadoBusca(
                List.of(trecho(0.71), trecho(null), trecho(0.93)), 0.70));

        ResultadoBusca resultado = servico.buscar(consulta);

        verify(chunkStore).buscar(consulta);
        assertThat(resultado.trechos()).extracting(Trecho::similaridade).containsExactly(0.93, 0.71, null);
        assertThat(resultado.limiarSimilaridade()).isEqualTo(0.70);
    }

    @Test
    void listaVaziaEResultadoValido() {
        var consulta = ConsultaBusca.de("sem relação", null);
        when(chunkStore.buscar(consulta)).thenReturn(new ResultadoBusca(List.of(), 0.70));

        assertThat(servico.buscar(consulta).trechos()).isEmpty();
    }

    private static Trecho trecho(Double similaridade) {
        return new Trecho(UUID.randomUUID(), "texto", UUID.randomUUID(), "Título", "fonte", Categoria.CURSO,
                similaridade);
    }
}

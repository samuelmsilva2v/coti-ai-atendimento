package br.com.coti.conhecimento.application;

import br.com.coti.conhecimento.domain.model.ConsultaBusca;
import br.com.coti.conhecimento.domain.model.ResultadoBusca;
import br.com.coti.conhecimento.domain.model.Trecho;
import br.com.coti.conhecimento.domain.port.ChunkStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Única implementação da busca semântica (RF04), usada pela ferramenta MCP e pelo endpoint de
 * diagnóstico. A consulta chega validada ({@link ConsultaBusca#de}), então entrada inválida nunca
 * gera embedding.
 */
@Service
public class BuscaSemanticaService {

    private static final Logger log = LoggerFactory.getLogger(BuscaSemanticaService.class);

    private static final Comparator<Trecho> SIMILARIDADE_DECRESCENTE = Comparator.comparing(Trecho::similaridade,
            Comparator.nullsLast(Comparator.reverseOrder()));

    private final ChunkStore chunkStore;

    public BuscaSemanticaService(ChunkStore chunkStore) {
        this.chunkStore = chunkStore;
    }

    public ResultadoBusca buscar(ConsultaBusca consulta) {
        long inicio = System.nanoTime();
        ResultadoBusca resultado = chunkStore.buscar(consulta);
        List<Trecho> ordenados = resultado.trechos().stream().sorted(SIMILARIDADE_DECRESCENTE).toList();

        // Só metadados: a pergunta e o texto dos trechos nunca vão para o log (Art. 13).
        log.atInfo()
                .addKeyValue("tamanhoPergunta", consulta.pergunta().length())
                .addKeyValue("limite", consulta.limite())
                .addKeyValue("quantidadeTrechos", ordenados.size())
                .addKeyValue("chunkIds", ordenados.stream().map(Trecho::chunkId).toList())
                .addKeyValue("similaridades", ordenados.stream().map(Trecho::similaridade).toList())
                .addKeyValue("latenciaMs", (System.nanoTime() - inicio) / 1_000_000)
                .log("Busca semântica executada");
        return new ResultadoBusca(ordenados, resultado.limiarSimilaridade());
    }
}

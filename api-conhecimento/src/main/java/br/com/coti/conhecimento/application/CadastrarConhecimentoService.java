package br.com.coti.conhecimento.application;

import br.com.coti.conhecimento.domain.model.Chunk;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.NovoConhecimento;
import br.com.coti.conhecimento.domain.port.ChunkStore;
import br.com.coti.conhecimento.domain.port.Chunker;
import br.com.coti.conhecimento.domain.port.ConhecimentoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Cadastro de conhecimento (RF01–RF03): divide em chunks, grava o documento e os chunks com
 * embeddings numa única transação. Qualquer falha desfaz tudo (RN01.2, ADR-008).
 */
@Service
public class CadastrarConhecimentoService {

    private static final Logger log = LoggerFactory.getLogger(CadastrarConhecimentoService.class);

    private final ConhecimentoRepository repositorio;
    private final Chunker chunker;
    private final ChunkStore chunkStore;

    public CadastrarConhecimentoService(ConhecimentoRepository repositorio, Chunker chunker, ChunkStore chunkStore) {
        this.repositorio = repositorio;
        this.chunker = chunker;
        this.chunkStore = chunkStore;
    }

    @Transactional
    public Conhecimento cadastrar(NovoConhecimento novo) {
        long inicio = System.nanoTime();
        List<Chunk> chunks = chunker.dividir(novo.conteudo());
        var conhecimento = new Conhecimento(
                UUID.randomUUID(),
                novo.titulo().strip(),
                novo.conteudo(),
                novo.categoria(),
                novo.fonte().strip(),
                novo.tags().stream().map(String::strip).toList(),
                chunks.size(),
                novo.conteudo().length(),
                Instant.now().truncatedTo(ChronoUnit.MILLIS));

        repositorio.inserir(conhecimento);
        chunkStore.adicionar(conhecimento, chunks);

        // Só metadados: o conteúdo nunca vai para o log (Art. 13).
        log.atInfo()
                .addKeyValue("documentoId", conhecimento.id())
                .addKeyValue("categoria", conhecimento.categoria())
                .addKeyValue("tamanhoConteudo", conhecimento.tamanhoConteudo())
                .addKeyValue("quantidadeChunks", conhecimento.quantidadeChunks())
                .addKeyValue("latenciaMs", (System.nanoTime() - inicio) / 1_000_000)
                .log("Conhecimento cadastrado");
        return conhecimento;
    }
}

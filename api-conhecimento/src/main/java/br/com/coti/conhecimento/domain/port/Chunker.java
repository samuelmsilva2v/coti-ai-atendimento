package br.com.coti.conhecimento.domain.port;

import br.com.coti.conhecimento.domain.model.Chunk;

import java.util.List;

public interface Chunker {

    /** Divide o texto em chunks ordenados, com sobreposição; texto não vazio gera ao menos 1 chunk. */
    List<Chunk> dividir(String texto);
}

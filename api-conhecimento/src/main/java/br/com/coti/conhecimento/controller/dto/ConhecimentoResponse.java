package br.com.coti.conhecimento.controller.dto;

import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Conhecimento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConhecimentoResponse(
        UUID id,
        String titulo,
        String conteudo,
        Categoria categoria,
        String fonte,
        List<String> tags,
        int quantidadeChunks,
        int tamanhoConteudo,
        Instant criadoEm) {

    public static ConhecimentoResponse de(Conhecimento c) {
        return new ConhecimentoResponse(c.id(), c.titulo(), c.conteudo(), c.categoria(), c.fonte(), c.tags(),
                c.quantidadeChunks(), c.tamanhoConteudo(), c.criadoEm());
    }
}

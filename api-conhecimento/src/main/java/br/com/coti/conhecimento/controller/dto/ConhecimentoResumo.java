package br.com.coti.conhecimento.controller.dto;

import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.ResumoConhecimento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Item de listagem: {@link ConhecimentoResponse} sem {@code conteudo}. */
public record ConhecimentoResumo(
        UUID id,
        String titulo,
        Categoria categoria,
        String fonte,
        List<String> tags,
        int quantidadeChunks,
        int tamanhoConteudo,
        Instant criadoEm) {

    public static ConhecimentoResumo de(ResumoConhecimento r) {
        return new ConhecimentoResumo(r.id(), r.titulo(), r.categoria(), r.fonte(), r.tags(), r.quantidadeChunks(),
                r.tamanhoConteudo(), r.criadoEm());
    }
}

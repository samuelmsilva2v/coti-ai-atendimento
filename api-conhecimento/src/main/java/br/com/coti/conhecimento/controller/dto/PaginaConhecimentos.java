package br.com.coti.conhecimento.controller.dto;

import br.com.coti.conhecimento.domain.model.Pagina;
import br.com.coti.conhecimento.domain.model.ResumoConhecimento;

import java.util.List;

public record PaginaConhecimentos(
        List<ConhecimentoResumo> itens,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas) {

    public static PaginaConhecimentos de(Pagina<ResumoConhecimento> p) {
        return new PaginaConhecimentos(p.itens().stream().map(ConhecimentoResumo::de).toList(), p.pagina(),
                p.tamanho(), p.totalItens(), p.totalPaginas());
    }
}

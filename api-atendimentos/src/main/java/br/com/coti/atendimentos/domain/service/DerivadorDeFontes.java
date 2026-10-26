package br.com.coti.atendimentos.domain.service;

import br.com.coti.atendimentos.domain.model.ContextoRag;
import br.com.coti.atendimentos.domain.model.Fonte;
import br.com.coti.atendimentos.domain.model.SaidaModelo;
import br.com.coti.atendimentos.domain.model.TrechoContexto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Deriva as fontes da resposta exclusivamente das evidências recuperadas (contrato, seção 6.4; Art. 9):
 * ids {@code Tn} inexistentes são descartados e contados; nenhum metadado vem do texto do modelo; a
 * ordem é a do contexto. Sem fonte válida, com {@code respondida=false} ou com resposta vazia, o
 * resultado é fallback.
 */
public class DerivadorDeFontes {

    public Derivacao derivar(SaidaModelo saida, ContextoRag contexto) {
        Set<String> citados = new HashSet<>(saida.fontesUsadas());
        long inexistentes = citados.stream().filter(id -> contexto.trecho(id).isEmpty()).count();
        List<Fonte> fontes = contexto.trechos().stream()
                .filter(t -> citados.contains(t.id()))
                .map(TrechoContexto::evidencia)
                .map(Fonte::de)
                .toList();
        boolean fallback = !saida.respondida() || fontes.isEmpty() || saida.resposta() == null
                || saida.resposta().isBlank();
        return new Derivacao(fallback ? List.of() : fontes, (int) inexistentes, fallback);
    }

    public record Derivacao(List<Fonte> fontes, int fontesDescartadas, boolean fallback) {
    }
}

package br.com.coti.atendimentos.domain.service;

import br.com.coti.atendimentos.domain.model.ContextoRag;
import br.com.coti.atendimentos.domain.model.Evidencia;
import br.com.coti.atendimentos.domain.model.Pergunta;
import br.com.coti.atendimentos.domain.model.TrechoContexto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Normaliza as evidências e monta o contexto RAG (contrato, seções 6.2 e 6.3):
 * <ol>
 *   <li>descarta texto vazio e deduplica por {@code chunkId};</li>
 *   <li>ordena por similaridade decrescente, com nulos no fim (ordem estável);</li>
 *   <li>inclui trechos <b>inteiros</b> enquanto a soma dos textos escapados couber em
 *       {@code maxCaracteres}; um trecho que não cabe é pulado (não truncado), e o laço continua;</li>
 *   <li>atribui ids locais {@code T1..Tn} e escapa todos os dados.</li>
 * </ol>
 * Contexto vazio significa fallback, sem chamar o modelo.
 */
public class MontadorDeContexto {

    private static final Comparator<Evidencia> SIMILARIDADE_DECRESCENTE = Comparator.comparing(
            Evidencia::similaridade, Comparator.nullsLast(Comparator.reverseOrder()));

    private final int maxCaracteres;

    public MontadorDeContexto(int maxCaracteres) {
        if (maxCaracteres <= 0) {
            throw new IllegalArgumentException("maxCaracteres deve ser positivo");
        }
        this.maxCaracteres = maxCaracteres;
    }

    public ContextoRag montar(Pergunta pergunta, List<Evidencia> evidencias) {
        Set<UUID> vistos = new HashSet<>();
        List<Evidencia> candidatas = evidencias.stream()
                .filter(e -> !e.texto().isBlank())
                .filter(e -> vistos.add(e.chunkId()))
                .sorted(SIMILARIDADE_DECRESCENTE)
                .toList();

        List<TrechoContexto> incluidos = new ArrayList<>();
        int usados = 0;
        for (Evidencia e : candidatas) {
            int tamanho = ContextoRag.escapar(e.texto()).length();
            if (usados + tamanho <= maxCaracteres) {
                incluidos.add(new TrechoContexto("T" + (incluidos.size() + 1), e));
                usados += tamanho;
            }
        }
        return new ContextoRag(ContextoRag.escapar(pergunta.texto()), incluidos);
    }
}

package br.com.coti.atendimentos.domain.model;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Contexto montado para o modelo (contrato, seção 6.3): pergunta e trechos já escapados, na ordem
 * T1..Tn. {@link #renderizarTrechos()} produz o conteúdo do bloco {@code <contexto>}.
 */
public record ContextoRag(String perguntaEscapada, List<TrechoContexto> trechos) {

    public ContextoRag {
        trechos = List.copyOf(trechos);
    }

    public boolean vazio() {
        return trechos.isEmpty();
    }

    public Optional<TrechoContexto> trecho(String id) {
        return trechos.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    public int tamanhoEmCaracteres() {
        return trechos.stream().mapToInt(t -> escapar(t.evidencia().texto()).length()).sum();
    }

    /** {@code <trecho id="Tn" titulo=".." categoria=".." fonte="..">texto</trecho>}, com todos os dados escapados. */
    public String renderizarTrechos() {
        return trechos.stream().map(t -> {
            Evidencia e = t.evidencia();
            return "<trecho id=\"" + t.id() + "\" titulo=\"" + escapar(e.titulo()) + "\" categoria=\""
                    + escapar(e.categoria()) + "\" fonte=\"" + escapar(e.fonte()) + "\">\n"
                    + escapar(e.texto()) + "\n</trecho>";
        }).collect(Collectors.joining("\n"));
    }

    /**
     * Escape do contrato (seção 6.3): {@code <} e {@code >} viram entidades, para que nenhum dado abra ou
     * feche delimitadores (Art. 10). Aspas também são escapadas, porque título e fonte vão em atributos.
     */
    public static String escapar(String valor) {
        return valor.replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

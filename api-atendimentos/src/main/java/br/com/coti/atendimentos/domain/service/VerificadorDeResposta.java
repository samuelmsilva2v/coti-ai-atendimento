package br.com.coti.atendimentos.domain.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verificação determinística da resposta gerada (contrato, seção 6.4, item 7; RN08.6). Recusa:
 * <ul>
 *   <li>{@link Recusa#VALOR_SEM_FONTE}: valor monetário, percentual, horário ou data que não aparece
 *       literalmente nos trechos citados (Art. 12);</li>
 *   <li>{@link Recusa#VAZAMENTO_DO_PROMPT}: linha do prompt de sistema na resposta (RN08.4).</li>
 * </ul>
 * É uma rede de segurança: a primeira defesa continua sendo o prompt de sistema.
 */
public class VerificadorDeResposta {

    public enum Recusa { VALOR_SEM_FONTE, VAZAMENTO_DO_PROMPT }

    static final int TAMANHO_MINIMO_LINHA_DO_PROMPT = 40;

    private static final Pattern VALOR = Pattern.compile(
            "R\\$\\s*\\d+(?:[.,]\\d+)*"                    // R$ 1.500,00 (sem pontuação final)
            + "|\\d+(?:[.,]\\d+)?\\s*%"                    // 10% / 12,5 %
            + "|\\b\\d{1,2}h(?:\\d{2})?\\b"                // 19h / 19h30
            + "|\\b\\d{1,2}:\\d{2}\\b"                     // 19:00
            + "|\\b\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?\\b",     // 10/02 / 10/02/2027
            Pattern.CASE_INSENSITIVE);

    private final List<String> linhasDoPrompt;

    public VerificadorDeResposta(String promptDeSistema) {
        this.linhasDoPrompt = promptDeSistema.lines()
                .map(VerificadorDeResposta::normalizarTexto)
                .filter(l -> l.length() >= TAMANHO_MINIMO_LINHA_DO_PROMPT)
                .toList();
    }

    public Optional<Recusa> verificar(String resposta, List<String> textosCitados) {
        String respostaNormalizada = normalizarTexto(resposta);
        if (linhasDoPrompt.stream().anyMatch(respostaNormalizada::contains)) {
            return Optional.of(Recusa.VAZAMENTO_DO_PROMPT);
        }
        String fontes = compactar(String.join(" ", textosCitados));
        Matcher valores = VALOR.matcher(resposta);
        while (valores.find()) {
            if (!fontes.contains(compactar(valores.group()))) {
                return Optional.of(Recusa.VALOR_SEM_FONTE);
            }
        }
        return Optional.empty();
    }

    /** Minúsculas e espaços colapsados, para comparar linhas do prompt. */
    private static String normalizarTexto(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").strip();
    }

    /** Minúsculas e sem espaços, para comparar valores ("R$ 1.500" = "r$1.500"). */
    private static String compactar(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}

package br.com.coti.conhecimento.infrastructure.chunking;

import br.com.coti.conhecimento.domain.model.Chunk;
import br.com.coti.conhecimento.domain.port.Chunker;
import br.com.coti.conhecimento.infrastructure.config.ChunkingProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Janela deslizante de tokens com sobreposição (ADR-009: o TokenTextSplitter do Spring AI 2.0 não
 * tem sobreposição). O texto é cortado só entre palavras (segmentos "palavra + espaços"), então
 * nunca se parte uma palavra nem um caractere multibyte. A contagem usa cl100k_base, o
 * codificador do text-embedding-3-small; somar os tokens por segmento é uma aproximação aceitável
 * do total do chunk.
 */
@Component
@EnableConfigurationProperties(ChunkingProperties.class)
public class TokenWindowChunker implements Chunker {

    private static final Pattern SEGMENTO = Pattern.compile("\\s*\\S+\\s*");

    private final Encoding codificador = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);
    private final int tamanho;
    private final int sobreposicao;

    public TokenWindowChunker(ChunkingProperties propriedades) {
        this.tamanho = propriedades.tamanhoTokens();
        this.sobreposicao = propriedades.sobreposicaoTokens();
    }

    @Override
    public List<Chunk> dividir(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("texto vazio não gera chunks");
        }
        List<Segmento> segmentos = segmentar(texto);

        List<int[]> janelas = new ArrayList<>();
        int inicio = 0;
        while (inicio < segmentos.size()) {
            int fim = inicio;
            int tokens = 0;
            while (fim < segmentos.size() && (fim == inicio || tokens + segmentos.get(fim).tokens <= tamanho)) {
                tokens += segmentos.get(fim++).tokens;
            }
            janelas.add(new int[] {inicio, fim});
            if (fim == segmentos.size()) {
                break;
            }
            // Recua do fim enquanto couber na sobreposição, sempre avançando ao menos um segmento.
            int proximo = fim;
            int sobrepostos = 0;
            while (proximo - 1 > inicio && sobrepostos + segmentos.get(proximo - 1).tokens <= sobreposicao) {
                sobrepostos += segmentos.get(--proximo).tokens;
            }
            inicio = proximo;
        }

        List<Chunk> chunks = new ArrayList<>(janelas.size());
        for (int i = 0; i < janelas.size(); i++) {
            int de = segmentos.get(janelas.get(i)[0]).inicio;
            Segmento ultimo = segmentos.get(janelas.get(i)[1] - 1);
            chunks.add(new Chunk(texto.substring(de, ultimo.fim), i, janelas.size(), de));
        }
        return chunks;
    }

    private List<Segmento> segmentar(String texto) {
        List<Segmento> segmentos = new ArrayList<>();
        Matcher m = SEGMENTO.matcher(texto);
        while (m.find()) {
            int tokens = codificador.countTokensOrdinary(m.group());
            if (tokens <= tamanho) {
                segmentos.add(new Segmento(m.start(), m.end(), tokens));
            } else {
                partirPalavraGigante(texto, m.start(), m.end(), segmentos);
            }
        }
        return segmentos;
    }

    /** Palavra sem espaços maior que um chunk (ex.: URL ou base64 enorme): corta por code points. */
    private void partirPalavraGigante(String texto, int de, int ate, List<Segmento> segmentos) {
        int passo = Math.max(1, tamanho / 4); // um code point raramente passa de 4 tokens
        int i = de;
        while (i < ate) {
            int j = Math.min(ate, texto.offsetByCodePoints(i, Math.min(passo, texto.codePointCount(i, ate))));
            segmentos.add(new Segmento(i, j, codificador.countTokensOrdinary(texto.substring(i, j))));
            i = j;
        }
    }

    private record Segmento(int inicio, int fim, int tokens) {
    }
}

package br.com.coti.conhecimento.support;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EmbeddingModel determinístico para testes (nunca chama a OpenAI). Usa "feature hashing" das
 * palavras em 1536 dimensões normalizadas: textos com palavras em comum têm similaridade de
 * cosseno maior, e textos idênticos têm similaridade 1. Permite simular falha a partir da N-ésima chamada.
 */
public class FakeEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSOES = 1536;
    private static final Pattern PALAVRA = Pattern.compile("[\\p{L}\\p{N}]+");

    private final AtomicInteger chamadas = new AtomicInteger();
    private volatile int falharNaChamada = Integer.MAX_VALUE;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        if (chamadas.incrementAndGet() >= falharNaChamada) {
            throw new IllegalStateException("falha simulada do provedor de embeddings (detalhe interno)");
        }
        List<Embedding> embeddings = new ArrayList<>();
        for (int i = 0; i < request.getInstructions().size(); i++) {
            embeddings.add(new Embedding(vetor(request.getInstructions().get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vetor(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSOES;
    }

    /** Falha a partir da {@code n}-ésima chamada a {@link #call} (1 = primeira). */
    public void falharAPartirDaChamada(int n) {
        falharNaChamada = n;
    }

    public int chamadas() {
        return chamadas.get();
    }

    public void reiniciar() {
        chamadas.set(0);
        falharNaChamada = Integer.MAX_VALUE;
    }

    public static float[] vetor(String texto) {
        float[] v = new float[DIMENSOES];
        Matcher m = PALAVRA.matcher(texto == null ? "" : texto.toLowerCase(Locale.ROOT));
        while (m.find()) {
            int hash = m.group().hashCode();
            v[Math.floorMod(hash, DIMENSOES)] += (hash & 1) == 0 ? 1f : -1f;
        }
        double norma = 0;
        for (float x : v) {
            norma += x * x;
        }
        if (norma == 0) {
            v[0] = 1f;
            return v;
        }
        float inv = (float) (1 / Math.sqrt(norma));
        for (int i = 0; i < v.length; i++) {
            v[i] *= inv;
        }
        return v;
    }
}

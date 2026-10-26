package br.com.coti.conhecimento.infrastructure.chunking;

import br.com.coti.conhecimento.domain.model.Chunk;
import br.com.coti.conhecimento.infrastructure.config.ChunkingProperties;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenWindowChunkerTest {

    private static final Encoding CL100K = Encodings.newDefaultEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    private final TokenWindowChunker chunker = new TokenWindowChunker(new ChunkingProperties(800, 100));

    @Test
    void documentoCurtoGeraUmChunk() {   // CA02.1
        List<Chunk> chunks = chunker.dividir("O curso Java Web Developer aborda Java 21 e Spring Boot.");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.getFirst().indice()).isZero();
        assertThat(chunks.getFirst().total()).isEqualTo(1);
        assertThat(chunks.getFirst().inicio()).isZero();
    }

    @Test
    void documentoLongoGeraChunksSequenciaisComSobreposicao() {   // CA02.2
        String texto = textoComTokens(2_000);

        List<Chunk> chunks = chunker.dividir(texto);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
        assertThat(chunks).extracting(Chunk::indice).containsExactlyElementsOf(IntStream.range(0, chunks.size()).boxed().toList());
        assertThat(chunks).allMatch(c -> c.total() == chunks.size());
        for (int i = 1; i < chunks.size(); i++) {
            Chunk anterior = chunks.get(i - 1);
            Chunk atual = chunks.get(i);
            int fimAnterior = anterior.inicio() + anterior.texto().length();
            assertThat(atual.inicio()).as("chunk %d começa antes do fim do anterior", i).isLessThan(fimAnterior);
            String sobreposto = texto.substring(atual.inicio(), fimAnterior);
            assertThat(anterior.texto()).endsWith(sobreposto);
            assertThat(atual.texto()).startsWith(sobreposto);
            assertThat(CL100K.countTokensOrdinary(sobreposto)).isBetween(50, 120);
        }
    }

    @Test
    void chunksRespeitamOTamanhoAproximado() {
        chunker.dividir(textoComTokens(5_000))
                .forEach(c -> assertThat(CL100K.countTokensOrdinary(c.texto())).isLessThanOrEqualTo(880));
    }

    @Test
    void reconstrucaoSemPerdaComAcentosEEmoji() {   // RN02.2
        String texto = IntStream.range(0, 600)
                .mapToObj(i -> "Pré-requisito nº" + i + ": lógica de programação, ação e coração 🚀😀.")
                .collect(Collectors.joining("\n"));

        List<Chunk> chunks = chunker.dividir(texto);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(reconstruir(chunks)).isEqualTo(texto);
        for (Chunk c : chunks) {
            assertThat(texto.substring(c.inicio(), c.inicio() + c.texto().length())).isEqualTo(c.texto());
            assertThat(c.texto()).doesNotContain("�");
            assertThat(Character.isLowSurrogate(c.texto().charAt(0))).isFalse();
        }
    }

    @Test
    void palavraGiganteSemEspacosEPartidaSemPerda() {
        String base64 = "QUJD".repeat(3_000) + "😀".repeat(500);

        List<Chunk> chunks = chunker.dividir(base64);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(reconstruir(chunks)).isEqualTo(base64);
        chunks.forEach(c -> assertThat(Character.isLowSurrogate(c.texto().charAt(0))).isFalse());
    }

    @Test
    void espacosNasPontasSaoPreservados() {
        String texto = "  \n texto com espaços nas pontas \n ";

        assertThat(reconstruir(chunker.dividir(texto))).isEqualTo(texto);
    }

    @Test
    void textoVazioERejeitado() {
        assertThatThrownBy(() -> chunker.dividir("   ")).isInstanceOf(IllegalArgumentException.class);
    }

    /** Junta os chunks descartando a parte sobreposta de cada um (usa o deslocamento de cada chunk). */
    private static String reconstruir(List<Chunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (Chunk c : chunks) {
            sb.append(c.texto().substring(sb.length() - c.inicio()));
        }
        return sb.toString();
    }

    private static String textoComTokens(int alvo) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (CL100K.countTokensOrdinary(sb.toString()) < alvo) {
            sb.append("O curso ").append(i++).append(" da COTI aborda Java, Spring Boot e bancos de dados. ");
        }
        return sb.toString();
    }
}

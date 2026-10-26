package br.com.coti.atendimentos.infrastructure.mcp;

import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException;
import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException.Motivo;
import br.com.coti.atendimentos.domain.model.Evidencia;
import br.com.coti.atendimentos.domain.model.Pergunta;
import br.com.coti.atendimentos.domain.port.BuscadorDeConhecimento;
import br.com.coti.fakemcp.FakeMcpServer;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cliente MCP real da aplicação contra o {@link FakeMcpServer} (CA07.1–CA07.3, CA10.3, CA10.4). */
@SpringBootTest
class McpBuscadorDeConhecimentoIT {

    private static final FakeMcpServer SERVIDOR = FakeMcpServer.iniciar();
    private static final String CORRELATION_ID = "8d2f9a4e-6b1c-4f3a-9e7d-2c5b8a1f0e34";

    @DynamicPropertySource
    static void apontarParaOServidorFalso(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.ai.mcp.client.streamable-http.connections.conhecimento.url", SERVIDOR::url);
        propriedades.add("spring.ai.mcp.client.request-timeout", () -> "2s");
    }

    @AfterAll
    static void parar() {
        SERVIDOR.close();
    }

    @Autowired
    private BuscadorDeConhecimento buscador;

    @BeforeEach
    void reiniciar() {
        SERVIDOR.comportamento().reiniciar();
        MDC.remove("correlationId");
    }

    @Test
    void chamaAFerramentaUmaVezComArgumentosEMeta() {   // CA07.1
        SERVIDOR.comportamento().responderTrechos(List.of(trecho(0.91), trecho(null)));
        MDC.put("correlationId", CORRELATION_ID);

        List<Evidencia> evidencias = buscador.buscar(new Pergunta("Quais os pré-requisitos?"), 5);

        assertThat(SERVIDOR.comportamento().recebidas()).hasSize(1);
        CallToolRequest recebida = SERVIDOR.comportamento().recebidas().getFirst();
        assertThat(recebida.name()).isEqualTo("buscar_conhecimento");
        assertThat(recebida.arguments()).containsEntry("pergunta", "Quais os pré-requisitos?").containsEntry("limite", 5);
        assertThat(recebida.meta()).containsEntry("correlationId", CORRELATION_ID);
        assertThat(evidencias).hasSize(2);
        assertThat(evidencias.getFirst().titulo()).isEqualTo("Curso Java Web Developer");
        assertThat(evidencias.getFirst().similaridade()).isEqualTo(0.91);
        assertThat(evidencias.get(1).similaridade()).isNull();
    }

    @Test
    void listaVaziaESemEvidenciaNaoErro() {
        SERVIDOR.comportamento().responderVazio();

        assertThat(buscador.buscar(new Pergunta("x"), 5)).isEmpty();
    }

    @Test
    void leOJsonDeContentQuandoNaoHaStructuredContent() {
        SERVIDOR.comportamento().responderSoComTexto("""
                {"trechos":[{"chunkId":"%s","texto":"t","documentoId":"%s","titulo":"T","fonte":"F",
                 "categoria":"CURSO","similaridade":0.8}],"total":1}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(buscador.buscar(new Pergunta("x"), 5)).singleElement()
                .extracting(Evidencia::similaridade).isEqualTo(0.8);
    }

    @Test
    void trechosIncompletosSaoDescartados() {
        Map<String, Object> semFonte = new HashMap<>(trecho(0.9));
        semFonte.remove("fonte");
        Map<String, Object> idInvalido = new HashMap<>(trecho(0.9));
        idInvalido.put("chunkId", "nao-e-uuid");

        SERVIDOR.comportamento().responderTrechos(List.of(semFonte, idInvalido, trecho(0.8)));

        assertThat(buscador.buscar(new Pergunta("x"), 5)).hasSize(1);
    }

    @Test
    void erroDaFerramentaGeraIndisponivel() {   // CA07.3
        SERVIDOR.comportamento().responderErroDaFerramenta();

        assertIndisponivel(Motivo.ERRO_DA_FERRAMENTA);
    }

    @Test
    void respostaForaDoEsquemaGeraIndisponivel() {
        SERVIDOR.comportamento().responderForaDoEsquema();

        assertIndisponivel(Motivo.RESPOSTA_INVALIDA);
    }

    @Test
    void timeoutGeraIndisponivel() {   // CA07.2 e CA10.4
        SERVIDOR.comportamento().atrasar(Duration.ofSeconds(4));
        long inicio = System.nanoTime();

        assertIndisponivel(Motivo.TIMEOUT);

        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(4));
    }

    private void assertIndisponivel(Motivo motivo) {
        assertThatThrownBy(() -> buscador.buscar(new Pergunta("x"), 5))
                .isInstanceOf(ConhecimentoIndisponivelException.class)
                .satisfies(e -> assertThat(((ConhecimentoIndisponivelException) e).motivo()).isEqualTo(motivo));
    }

    private static Map<String, Object> trecho(Double similaridade) {
        Map<String, Object> t = new HashMap<>();
        t.put("chunkId", UUID.randomUUID().toString());
        t.put("texto", "Pré-requisito: lógica de programação.");
        t.put("documentoId", UUID.randomUUID().toString());
        t.put("titulo", "Curso Java Web Developer");
        t.put("fonte", "https://www.cotiinformatica.com.br/cursos/java-web-developer");
        t.put("categoria", "CURSO");
        t.put("similaridade", similaridade);
        return t;
    }
}

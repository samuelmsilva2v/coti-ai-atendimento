package br.com.coti.atendimentos.controller;

import br.com.coti.atendimentos.application.ResponderPerguntaService;
import br.com.coti.atendimentos.domain.exception.ConhecimentoIndisponivelException;
import br.com.coti.atendimentos.domain.model.Pergunta;
import br.com.coti.atendimentos.domain.model.Resposta;
import br.com.coti.atendimentos.infrastructure.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebMvcTest(AtendimentoController.class)
class AtendimentoControllerTest {

    private static final String CORRELATION_ID = "8d2f9a4e-6b1c-4f3a-9e7d-2c5b8a1f0e34";
    private static final String FALLBACK = "Não encontrei essa informação na base de conhecimento da COTI.";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ResponderPerguntaService responder;

    @Test
    void perguntaValidaGera200NoFormatoDoContrato() {   // CA06.1 e CA06.4
        when(responder.responder(any())).thenReturn(Resposta.fallback(FALLBACK));

        var resultado = perguntar("{\"pergunta\":\"  Qual o horário do curso?  \"}", CORRELATION_ID);

        assertThat(resultado).hasStatusOk();
        assertThat(resultado.getResponse().getHeader(CorrelationIdFilter.HEADER)).isEqualTo(CORRELATION_ID);
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.correlationId").isEqualTo(CORRELATION_ID);
        json.extractingPath("$.resposta").isEqualTo(FALLBACK);
        json.extractingPath("$.respondida").isEqualTo(false);
        json.extractingPath("$.fallback").isEqualTo(true);
        json.extractingPath("$.fontes").asArray().isEmpty();
        json.extractingPath("$.respondidoEm").asString().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");
        verify(responder).responder(new Pergunta("Qual o horário do curso?"));
    }

    @Test
    void correlationIdGeradoQuandoAusenteEIgualNoCorpoENoCabecalho() {
        when(responder.responder(any())).thenReturn(Resposta.fallback(FALLBACK));

        var resultado = perguntar("{\"pergunta\":\"x\"}", null);

        String noCabecalho = resultado.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(noCabecalho).hasSize(36);
        assertThat(resultado).bodyJson().extractingPath("$.correlationId").isEqualTo(noCabecalho);
    }

    @Test
    void perguntaAusenteOuEmBrancoGera400SemChamarOServico() {   // CA06.2
        assertErro(perguntar("{}", null), 400, "VALIDACAO_FALHOU");
        assertErro(perguntar("{\"pergunta\":\"   \"}", null), 400, "VALIDACAO_FALHOU");
        verifyNoInteractions(responder);
    }

    @Test
    void perguntaLongaDemaisGera400() {   // CA06.3
        assertErro(perguntar("{\"pergunta\":\"" + "x".repeat(1001) + "\"}", null), 400, "VALIDACAO_FALHOU");
        verifyNoInteractions(responder);
    }

    @Test
    void jsonMalformadoEMidiaIncorreta() {
        assertErro(perguntar("{\"pergunta\":", null), 400, "JSON_INVALIDO");
        var textoPuro = mvc.post().uri("/api/atendimentos/perguntar").contentType(MediaType.TEXT_PLAIN)
                .content("pergunta").exchange();
        assertErro(textoPuro, 415, "TIPO_MIDIA_NAO_SUPORTADO");
    }

    @Test
    void conhecimentoIndisponivelGera503ComRetryAfter() {   // CA10.3 (mapeamento)
        when(responder.responder(any())).thenThrow(new ConhecimentoIndisponivelException(
                ConhecimentoIndisponivelException.Motivo.CONEXAO, new RuntimeException("Connection refused localhost:8081")));

        var resultado = perguntar("{\"pergunta\":\"x\"}", CORRELATION_ID);

        assertErro(resultado, 503, "CONHECIMENTO_INDISPONIVEL");
        assertThat(resultado.getResponse().getHeader("Retry-After")).isEqualTo("5");
        assertThat(resultado).bodyJson().extractingPath("$.correlationId").isEqualTo(CORRELATION_ID);
        assertThat(resultado).bodyJson().extractingPath("$.detail").asString().doesNotContain("localhost", "refused");
    }

    private MvcTestResult perguntar(String corpo, String correlationId) {
        var requisicao = mvc.post().uri("/api/atendimentos/perguntar").contentType(MediaType.APPLICATION_JSON).content(corpo);
        return correlationId == null ? requisicao.exchange()
                : requisicao.header(CorrelationIdFilter.HEADER, correlationId).exchange();
    }

    private static void assertErro(MvcTestResult resultado, int status, String codigo) {
        assertThat(resultado).hasStatus(status);
        assertThat(resultado.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
    }
}

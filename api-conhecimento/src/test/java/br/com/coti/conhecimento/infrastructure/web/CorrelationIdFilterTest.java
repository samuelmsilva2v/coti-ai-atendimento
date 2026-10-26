package br.com.coti.conhecimento.infrastructure.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filtro = new CorrelationIdFilter();

    @Test
    void geraIdQuandoCabecalhoAusente() throws Exception {
        var resultado = executar(null);

        assertThat(UUID.fromString(resultado.noMdc)).isNotNull();
        assertThat(resultado.noCabecalho).isEqualTo(resultado.noMdc);
    }

    @Test
    void mantemUuidValidoRecebido() throws Exception {
        String recebido = "8D2F9A4E-6B1C-4F3A-9E7D-2C5B8A1F0E34";

        var resultado = executar(recebido);

        assertThat(resultado.noMdc).isEqualTo(recebido.toLowerCase());
        assertThat(resultado.noCabecalho).isEqualTo(recebido.toLowerCase());
    }

    @Test
    void substituiValorInvalido() throws Exception {
        var resultado = executar("nao-e-uuid<script>");

        assertThat(resultado.noMdc).isNotEqualTo("nao-e-uuid<script>");
        assertThat(UUID.fromString(resultado.noMdc)).isNotNull();
        assertThat(resultado.noCabecalho).isEqualTo(resultado.noMdc);
    }

    @Test
    void rejeitaUuidNaoCanonico() throws Exception {
        var resultado = executar("1-1-1-1-1");

        assertThat(resultado.noMdc).isNotEqualTo("1-1-1-1-1");
    }

    @Test
    void limpaMdcAoFinal() throws Exception {
        executar(null);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    private Resultado executar(String cabecalho) throws Exception {
        var request = new MockHttpServletRequest("GET", "/qualquer");
        if (cabecalho != null) {
            request.addHeader(CorrelationIdFilter.HEADER, cabecalho);
        }
        var response = new MockHttpServletResponse();
        var noMdc = new AtomicReference<String>();

        filtro.doFilter(request, response, (req, res) -> noMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        return new Resultado(noMdc.get(), response.getHeader(CorrelationIdFilter.HEADER));
    }

    private record Resultado(String noMdc, String noCabecalho) {
    }
}

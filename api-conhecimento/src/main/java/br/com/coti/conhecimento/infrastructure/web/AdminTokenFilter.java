package br.com.coti.conhecimento.infrastructure.web;

import br.com.coti.conhecimento.infrastructure.config.AdminProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Protege {@code /api/conhecimentos/**} com o cabeçalho {@value #HEADER} (ADR-010). Roda antes do
 * DispatcherServlet, então o 401 vem antes de qualquer validação do corpo. O erro é delegado ao
 * {@link GlobalExceptionHandler} para manter o mesmo formato Problem Details. O valor do token
 * nunca é registrado em log.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@EnableConfigurationProperties(AdminProperties.class)
public class AdminTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Admin-Token";
    private static final String CAMINHO_PROTEGIDO = "/api/conhecimentos";

    private final byte[] resumoEsperado;
    private final HandlerExceptionResolver resolvedorDeErros;

    public AdminTokenFilter(AdminProperties propriedades,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolvedorDeErros) {
        this.resumoEsperado = resumo(propriedades.token());
        this.resolvedorDeErros = resolvedorDeErros;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho = request.getRequestURI().substring(request.getContextPath().length());
        return !(caminho.equals(CAMINHO_PROTEGIDO) || caminho.startsWith(CAMINHO_PROTEGIDO + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String recebido = request.getHeader(HEADER);
        // Compara resumos SHA-256 para que o tempo não dependa do conteúdo nem do tamanho do token.
        if (recebido == null || !MessageDigest.isEqual(resumoEsperado, resumo(recebido))) {
            resolvedorDeErros.resolveException(request, response, null, new AcessoNaoAutorizadoException());
            return;
        }
        chain.doFilter(request, response);
    }

    private static byte[] resumo(String valor) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}

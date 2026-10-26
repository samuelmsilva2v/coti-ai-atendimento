package br.com.coti.conhecimento.infrastructure.web;

import br.com.coti.conhecimento.domain.exception.ConhecimentoNaoEncontradoException;
import br.com.coti.conhecimento.domain.exception.ConsultaInvalidaException;
import br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.databind.exc.InvalidFormatException;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Converte toda exceção em Problem Details (RFC 9457) com as extensões do contrato:
 * {@code codigo}, {@code correlationId}, {@code timestamp} e, em validação, {@code erros}.
 * Nunca expõe stack trace nem mensagens internas.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErroCampo> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ErroCampo(e.getField(), e.getDefaultMessage()))
                .toList();
        return resposta(CodigoErro.VALIDACAO_FALHOU, request, erros);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ErroCampo> erros = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new ErroCampo(nomeParametro(r), e.getDefaultMessage())))
                .toList();
        return resposta(CodigoErro.VALIDACAO_FALHOU, request, erros);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String campo = ex.getPropertyName() != null ? ex.getPropertyName() : "parametro";
        return resposta(CodigoErro.VALIDACAO_FALHOU, request, List.of(new ErroCampo(campo, "valor inválido")));
    }

    /** Enum inválido (ex.: categoria) é erro de validação do campo; demais problemas de leitura são JSON_INVALIDO. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.getCause() instanceof InvalidFormatException formato
                && formato.getTargetType() != null && formato.getTargetType().isEnum()
                && !formato.getPath().isEmpty()) {
            String campo = formato.getPath().getLast().getPropertyName();
            return resposta(CodigoErro.VALIDACAO_FALHOU, request, List.of(new ErroCampo(campo, "valor inválido")));
        }
        return resposta(CodigoErro.JSON_INVALIDO, request, null);
    }

    @ExceptionHandler(AcessoNaoAutorizadoException.class)
    ResponseEntity<Object> handleNaoAutorizado(AcessoNaoAutorizadoException ex, WebRequest request) {
        return ResponseEntity.status(CodigoErro.NAO_AUTORIZADO.status())
                .header(HttpHeaders.WWW_AUTHENTICATE, AdminTokenFilter.HEADER)
                .body(problema(CodigoErro.NAO_AUTORIZADO, CodigoErro.NAO_AUTORIZADO.status(), request, null));
    }

    @ExceptionHandler(DependenciaIndisponivelException.class)
    ResponseEntity<Object> handleDependencia(DependenciaIndisponivelException ex, WebRequest request) {
        CodigoErro codigo = ex.dependencia() == DependenciaIndisponivelException.Dependencia.EMBEDDING
                ? CodigoErro.EMBEDDING_INDISPONIVEL
                : CodigoErro.BANCO_INDISPONIVEL;
        return indisponivel(codigo, ex, request);
    }

    /** Banco inacessível ou lento (conexão, timeout). Violações de integridade continuam 500. */
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessException.class})
    ResponseEntity<Object> handleBancoIndisponivel(DataAccessException ex, WebRequest request) {
        return indisponivel(CodigoErro.BANCO_INDISPONIVEL, ex, request);
    }

    @ExceptionHandler(ConsultaInvalidaException.class)
    ResponseEntity<Object> handleConsultaInvalida(ConsultaInvalidaException ex, WebRequest request) {
        return resposta(CodigoErro.VALIDACAO_FALHOU, request, List.of(new ErroCampo(ex.campo(), ex.getMessage())));
    }

    @ExceptionHandler(ConhecimentoNaoEncontradoException.class)
    ResponseEntity<Object> handleNaoEncontrado(ConhecimentoNaoEncontradoException ex, WebRequest request) {
        return resposta(CodigoErro.RECURSO_NAO_ENCONTRADO, request, null);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<ErroCampo> erros = ex.getConstraintViolations().stream()
                .map(v -> new ErroCampo(ultimoNo(v.getPropertyPath().toString()), v.getMessage()))
                .toList();
        return resposta(CodigoErro.VALIDACAO_FALHOU, request, erros);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleInesperado(Exception ex, WebRequest request) {
        log.error("Erro inesperado ao processar a requisição", ex);
        return resposta(CodigoErro.ERRO_INTERNO, request, null);
    }

    /** Demais exceções padrão do Spring MVC (404, 405, 415...): mapeadas pelo status. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        CodigoErro codigo = switch (statusCode.value()) {
            case 404 -> CodigoErro.RECURSO_NAO_ENCONTRADO;
            case 405 -> CodigoErro.METODO_NAO_SUPORTADO;
            case 415 -> CodigoErro.TIPO_MIDIA_NAO_SUPORTADO;
            default -> statusCode.is4xxClientError() ? CodigoErro.VALIDACAO_FALHOU : CodigoErro.ERRO_INTERNO;
        };
        if (codigo == CodigoErro.ERRO_INTERNO) {
            log.error("Erro do framework ao processar a requisição", ex);
        }
        ProblemDetail problema = problema(codigo, statusCode, request, null);
        return ResponseEntity.status(statusCode).headers(headers).body(problema);
    }

    private ResponseEntity<Object> indisponivel(CodigoErro codigo, Exception ex, WebRequest request) {
        // A causa (mensagem do provedor/driver) fica só no log, nunca no corpo da resposta.
        log.warn("Dependência indisponível: {}", codigo, ex);
        return ResponseEntity.status(codigo.status())
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(problema(codigo, codigo.status(), request, null));
    }

    private ResponseEntity<Object> resposta(CodigoErro codigo, WebRequest request, List<ErroCampo> erros) {
        return ResponseEntity.status(codigo.status()).body(problema(codigo, codigo.status(), request, erros));
    }

    private ProblemDetail problema(CodigoErro codigo, HttpStatusCode status, WebRequest request,
            List<ErroCampo> erros) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, codigo.detalhePadrao());
        problema.setType(codigo.tipo());
        problema.setTitle(codigo.titulo());
        problema.setInstance(URI.create(caminho(request)));
        problema.setProperty("codigo", codigo.name());
        problema.setProperty("correlationId", correlationId());
        problema.setProperty("timestamp", Instant.now().toString());
        if (erros != null && !erros.isEmpty()) {
            problema.setProperty("erros", erros);
        }
        return problema;
    }

    private static String correlationId() {
        String id = MDC.get(CorrelationIdFilter.MDC_KEY);
        return id != null ? id : UUID.randomUUID().toString();
    }

    private static String caminho(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : "/";
    }

    private static String nomeParametro(ParameterValidationResult resultado) {
        String nome = resultado.getMethodParameter().getParameterName();
        return nome != null ? nome : "parametro";
    }

    private static String ultimoNo(String caminho) {
        int ponto = caminho.lastIndexOf('.');
        return ponto >= 0 ? caminho.substring(ponto + 1) : caminho;
    }
}

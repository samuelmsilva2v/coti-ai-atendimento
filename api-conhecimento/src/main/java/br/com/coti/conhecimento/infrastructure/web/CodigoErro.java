package br.com.coti.conhecimento.infrastructure.web;

import org.springframework.http.HttpStatus;

import java.net.URI;
import java.util.Locale;

/** Catálogo de códigos de erro (specs/06-api-contracts.md, seção 2). */
public enum CodigoErro {

    VALIDACAO_FALHOU(HttpStatus.BAD_REQUEST, "Requisição inválida",
            "Um ou mais campos são inválidos."),
    JSON_INVALIDO(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido",
            "O corpo da requisição não é um JSON válido ou contém tipos incorretos."),
    NAO_AUTORIZADO(HttpStatus.UNAUTHORIZED, "Não autorizado",
            "Credencial administrativa ausente ou inválida."),
    RECURSO_NAO_ENCONTRADO(HttpStatus.NOT_FOUND, "Recurso não encontrado",
            "O recurso solicitado não existe."),
    METODO_NAO_SUPORTADO(HttpStatus.METHOD_NOT_ALLOWED, "Método não suportado",
            "O método HTTP não é suportado para este recurso."),
    TIPO_MIDIA_NAO_SUPORTADO(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Tipo de mídia não suportado",
            "Use Content-Type: application/json."),
    EMBEDDING_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "Serviço de embeddings indisponível",
            "Não foi possível processar o conteúdo no momento. Tente novamente em instantes."),
    BANCO_INDISPONIVEL(HttpStatus.SERVICE_UNAVAILABLE, "Base de conhecimento indisponível",
            "A base de conhecimento está temporariamente indisponível. Tente novamente em instantes."),
    ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
            "Ocorreu um erro inesperado. Tente novamente mais tarde.");

    private final HttpStatus status;
    private final String titulo;
    private final String detalhePadrao;

    CodigoErro(HttpStatus status, String titulo, String detalhePadrao) {
        this.status = status;
        this.titulo = titulo;
        this.detalhePadrao = detalhePadrao;
    }

    public HttpStatus status() {
        return status;
    }

    public String titulo() {
        return titulo;
    }

    public String detalhePadrao() {
        return detalhePadrao;
    }

    public URI tipo() {
        return URI.create("https://coti.com.br/problemas/" + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }
}

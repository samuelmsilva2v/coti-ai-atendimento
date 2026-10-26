package br.com.coti.conhecimento.mcp.dto;

/** {@code structuredContent} de erro da ferramenta, com {@code isError=true} (contrato, seção 4.5). */
public record ErroMcp(Detalhe erro) {

    public enum Codigo { ARGUMENTO_INVALIDO, EMBEDDING_INDISPONIVEL, BANCO_INDISPONIVEL, ERRO_INTERNO }

    public record Detalhe(Codigo codigo, String mensagem) {
    }

    public static ErroMcp de(Codigo codigo, String mensagem) {
        return new ErroMcp(new Detalhe(codigo, mensagem));
    }
}

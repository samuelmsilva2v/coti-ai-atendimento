package br.com.coti.conhecimento.controller.dto;

import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.NovoConhecimento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Corpo de {@code POST /api/conhecimentos} (specs/06-api-contracts.md, seção 3). */
public record CadastrarConhecimentoRequest(
        @Schema(example = "Curso Java Web Developer")
        @NotBlank @Size(max = 200) String titulo,
        @Schema(example = "O curso Java Web Developer aborda Java 21, Spring Boot e APIs REST. Pré-requisito: lógica de programação.")
        @NotBlank @Size(max = 100_000) String conteudo,
        @Schema(example = "CURSO")
        @NotNull Categoria categoria,
        @Schema(description = "Origem oficial do conteúdo", example = "https://www.cotiinformatica.com.br/cursos/java-web-developer")
        @NotBlank @Size(max = 500) String fonte,
        @Schema(example = "[\"java\", \"spring\", \"backend\"]")
        @Size(max = 10) List<@NotBlank @Size(max = 50) String> tags) {

    public NovoConhecimento paraDominio() {
        return new NovoConhecimento(titulo, conteudo, categoria, fonte, tags);
    }
}

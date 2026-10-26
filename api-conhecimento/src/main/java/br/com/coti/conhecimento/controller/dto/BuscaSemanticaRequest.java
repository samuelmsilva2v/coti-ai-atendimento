package br.com.coti.conhecimento.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/conhecimentos/busca}; a pergunta vai no corpo para não aparecer em logs de URL. */
public record BuscaSemanticaRequest(
        @Schema(example = "Quais tecnologias são ensinadas no curso Java Web Developer?")
        @NotBlank @Size(max = 1000) String pergunta,
        @Schema(description = "Padrão 5", example = "3")
        @Min(1) @Max(10) Integer limite) {
}

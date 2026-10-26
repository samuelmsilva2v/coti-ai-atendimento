package br.com.coti.atendimentos.controller.dto;

import br.com.coti.atendimentos.domain.model.Pergunta;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Corpo de {@code POST /api/atendimentos/perguntar} (specs/06-api-contracts.md, seção 5). */
public record PerguntaRequest(
        @Schema(example = "Quais os pré-requisitos do curso Java Web Developer?")
        @NotBlank @Size(max = 1000) String pergunta) {

    public Pergunta paraDominio() {
        return new Pergunta(pergunta.strip());
    }
}

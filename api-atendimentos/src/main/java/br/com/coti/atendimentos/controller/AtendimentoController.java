package br.com.coti.atendimentos.controller;

import br.com.coti.atendimentos.application.ResponderPerguntaService;
import br.com.coti.atendimentos.controller.dto.PerguntaRequest;
import br.com.coti.atendimentos.controller.dto.RespostaAtendimentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** API pública de perguntas (specs/06-api-contracts.md, seção 5.1). */
@RestController
@Tag(name = "Atendimentos", description = "Perguntas de alunos respondidas só com a base de conhecimento da COTI")
public class AtendimentoController {

    private static final String PROBLEMA = "application/problem+json";

    private final ResponderPerguntaService responder;

    public AtendimentoController(ResponderPerguntaService responder) {
        this.responder = responder;
    }

    /** O {@code correlationId} é definido pelo filtro de correlação (atributo da requisição e cabeçalho). */
    @Operation(summary = "Perguntar",
            description = "Busca na base via MCP e gera a resposta só com os trechos recuperados. Sem evidência, devolve "
                    + "a mensagem de fallback (respondida=false, fallback=true, fontes=[]).",
            parameters = @Parameter(name = "X-Correlation-Id", in = ParameterIn.HEADER, required = false,
                    description = "UUID opcional; gerado quando ausente ou inválido e sempre devolvido",
                    schema = @Schema(type = "string", format = "uuid")))
    @ApiResponse(responseCode = "200", description = "Resposta fundamentada ou fallback por ausência de evidência")
    @ApiResponse(responseCode = "400", description = "VALIDACAO_FALHOU ou JSON_INVALIDO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "TIPO_MIDIA_NAO_SUPORTADO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "CONHECIMENTO_INDISPONIVEL ou MODELO_INDISPONIVEL (Retry-After: 5)",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "ERRO_INTERNO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(path = "/api/atendimentos/perguntar", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RespostaAtendimentoResponse perguntar(@Valid @RequestBody PerguntaRequest requisicao,
            @Parameter(hidden = true) @RequestAttribute("correlationId") String correlationId) {
        return RespostaAtendimentoResponse.de(responder.responder(requisicao.paraDominio()), UUID.fromString(correlationId));
    }
}

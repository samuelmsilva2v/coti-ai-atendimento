package br.com.coti.conhecimento.controller;

import br.com.coti.conhecimento.application.BuscaSemanticaService;
import br.com.coti.conhecimento.controller.dto.BuscaSemanticaRequest;
import br.com.coti.conhecimento.controller.dto.BuscaSemanticaResponse;
import br.com.coti.conhecimento.domain.model.ConsultaBusca;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Busca semântica administrativa, só para diagnóstico e calibração do limiar (contrato, seção 3.4).
 * Protegida pelo token administrativo; a api-atendimentos NÃO usa este endpoint (Art. 4).
 */
@RestController
@Tag(name = "Conhecimentos")
@SecurityRequirement(name = "adminToken")
public class BuscaDiagnosticoController {

    private static final String PROBLEMA = "application/problem+json";

    private final BuscaSemanticaService busca;

    public BuscaDiagnosticoController(BuscaSemanticaService busca) {
        this.busca = busca;
    }

    @Operation(summary = "Busca semântica (diagnóstico)",
            description = "Mesma busca da ferramenta MCP buscar_conhecimento, para calibração do limiar. "
                    + "Não é usada pela api-atendimentos.")
    @ApiResponse(responseCode = "200", description = "Trechos acima do limiar (lista pode ser vazia)")
    @ApiResponse(responseCode = "400", description = "VALIDACAO_FALHOU ou JSON_INVALIDO; nenhum embedding é gerado",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "NAO_AUTORIZADO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "EMBEDDING_INDISPONIVEL ou BANCO_INDISPONIVEL",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(path = "/api/conhecimentos/busca", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BuscaSemanticaResponse buscar(@Valid @RequestBody BuscaSemanticaRequest requisicao) {
        return BuscaSemanticaResponse.de(busca.buscar(ConsultaBusca.de(requisicao.pergunta(), requisicao.limite())));
    }
}

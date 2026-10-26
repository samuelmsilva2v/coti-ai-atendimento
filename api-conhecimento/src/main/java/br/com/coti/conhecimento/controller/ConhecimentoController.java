package br.com.coti.conhecimento.controller;

import br.com.coti.conhecimento.application.CadastrarConhecimentoService;
import br.com.coti.conhecimento.application.ConsultarConhecimentoService;
import br.com.coti.conhecimento.controller.dto.CadastrarConhecimentoRequest;
import br.com.coti.conhecimento.controller.dto.ConhecimentoResponse;
import br.com.coti.conhecimento.controller.dto.PaginaConhecimentos;
import br.com.coti.conhecimento.domain.model.Categoria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Optional;
import java.util.UUID;

/** REST administrativo da base de conhecimento (specs/06-api-contracts.md, seções 3.1 a 3.3). */
@RestController
@RequestMapping("/api/conhecimentos")
@Tag(name = "Conhecimentos", description = "Cadastro e consulta da base de conhecimento (administrativo)")
@SecurityRequirement(name = "adminToken")
public class ConhecimentoController {

    private static final String PROBLEMA = "application/problem+json";

    private final CadastrarConhecimentoService cadastrar;
    private final ConsultarConhecimentoService consultar;

    public ConhecimentoController(CadastrarConhecimentoService cadastrar, ConsultarConhecimentoService consultar) {
        this.cadastrar = cadastrar;
        this.consultar = consultar;
    }

    @Operation(summary = "Cadastrar conhecimento",
            description = "Divide em chunks, gera embeddings e grava no pgvector de forma síncrona e atômica.")
    @ApiResponse(responseCode = "201", description = "Cadastrado; cabeçalho Location aponta para o recurso")
    @ApiResponse(responseCode = "400", description = "VALIDACAO_FALHOU ou JSON_INVALIDO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "NAO_AUTORIZADO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415", description = "TIPO_MIDIA_NAO_SUPORTADO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "EMBEDDING_INDISPONIVEL ou BANCO_INDISPONIVEL; nada é persistido",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "ERRO_INTERNO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConhecimentoResponse> cadastrar(@Valid @RequestBody CadastrarConhecimentoRequest requisicao) {
        var conhecimento = cadastrar.cadastrar(requisicao.paraDominio());
        var local = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/conhecimentos/{id}").buildAndExpand(conhecimento.id()).toUri();
        return ResponseEntity.created(local).body(ConhecimentoResponse.de(conhecimento));
    }

    @Operation(summary = "Listar conhecimentos", description = "Paginado, ordenado por criadoEm decrescente, sem o conteúdo.")
    @ApiResponse(responseCode = "200", description = "Página (lista vazia não é 404)")
    @ApiResponse(responseCode = "400", description = "VALIDACAO_FALHOU (pagina, tamanho ou categoria inválidos)",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "NAO_AUTORIZADO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "BANCO_INDISPONIVEL",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public PaginaConhecimentos listar(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanho,
            @RequestParam(required = false) Categoria categoria) {
        return PaginaConhecimentos.de(consultar.listar(Optional.ofNullable(categoria), pagina, tamanho));
    }

    @Operation(summary = "Consultar conhecimento por id", description = "Inclui o conteúdo integral.")
    @ApiResponse(responseCode = "200", description = "Encontrado")
    @ApiResponse(responseCode = "400", description = "VALIDACAO_FALHOU (id não é UUID)",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "NAO_AUTORIZADO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "RECURSO_NAO_ENCONTRADO",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "BANCO_INDISPONIVEL",
            content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{id}")
    public ConhecimentoResponse buscar(@PathVariable UUID id) {
        return ConhecimentoResponse.de(consultar.buscarPorId(id));
    }
}

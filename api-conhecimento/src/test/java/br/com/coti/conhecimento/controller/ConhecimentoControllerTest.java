package br.com.coti.conhecimento.controller;

import br.com.coti.conhecimento.application.CadastrarConhecimentoService;
import br.com.coti.conhecimento.application.ConsultarConhecimentoService;
import br.com.coti.conhecimento.domain.exception.ConhecimentoNaoEncontradoException;
import br.com.coti.conhecimento.domain.exception.DependenciaIndisponivelException;
import br.com.coti.conhecimento.domain.model.Categoria;
import br.com.coti.conhecimento.domain.model.Conhecimento;
import br.com.coti.conhecimento.domain.model.Pagina;
import br.com.coti.conhecimento.infrastructure.web.AdminTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@WebMvcTest(ConhecimentoController.class)
class ConhecimentoControllerTest {

    private static final String TOKEN = "token-de-teste-0123456789";
    private static final String CORPO_VALIDO = """
            {"titulo":"Curso Java Web Developer","conteudo":"Java 21 e Spring Boot.","categoria":"CURSO",
             "fonte":"https://www.cotiinformatica.com.br/cursos/java-web-developer","tags":["java"]}
            """;

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private CadastrarConhecimentoService cadastrar;

    @MockitoBean
    private ConsultarConhecimentoService consultar;

    @Test
    void cadastroValidoGera201ComLocation() {
        var salvo = conhecimento();
        when(cadastrar.cadastrar(any())).thenReturn(salvo);

        var resultado = post(CORPO_VALIDO, MediaType.APPLICATION_JSON);

        assertThat(resultado).hasStatus(201);
        assertThat(resultado.getResponse().getHeader("Location")).endsWith("/api/conhecimentos/" + salvo.id());
        var json = assertThat(resultado).bodyJson();
        json.extractingPath("$.id").isEqualTo(salvo.id().toString());
        json.extractingPath("$.categoria").isEqualTo("CURSO");
        json.extractingPath("$.criadoEm").isEqualTo("2026-10-25T18:30:00Z");
    }

    @Test
    void semConteudoGera400ESemChamarOServico() {   // CA01.2
        var resultado = post("""
                {"titulo":"T","categoria":"CURSO","fonte":"F"}
                """, MediaType.APPLICATION_JSON);

        assertErro(resultado, 400, "VALIDACAO_FALHOU");
        assertThat(resultado).bodyJson().extractingPath("$.erros[0].campo").isEqualTo("conteudo");
        verifyNoInteractions(cadastrar);
    }

    @Test
    void categoriaInvalidaGera400NoCampoCategoria() {   // CA01.3
        var resultado = post(CORPO_VALIDO.replace("\"CURSO\"", "\"PROMOCAO\""), MediaType.APPLICATION_JSON);

        assertErro(resultado, 400, "VALIDACAO_FALHOU");
        assertThat(resultado).bodyJson().extractingPath("$.erros[0].campo").isEqualTo("categoria");
        verifyNoInteractions(cadastrar);
    }

    @Test
    void conteudoAcimaDoLimiteGera400() {   // CA01.4
        String corpo = CORPO_VALIDO.replace("Java 21 e Spring Boot.", "x".repeat(100_001));

        assertErro(post(corpo, MediaType.APPLICATION_JSON), 400, "VALIDACAO_FALHOU");
        verifyNoInteractions(cadastrar);
    }

    @Test
    void semTokenGera401AntesDaValidacao() {   // CA01.5
        var resultado = mvc.post().uri("/api/conhecimentos").contentType(MediaType.APPLICATION_JSON)
                .content("{}").exchange();

        assertErro(resultado, 401, "NAO_AUTORIZADO");
        verifyNoInteractions(cadastrar);
    }

    @Test
    void falhaDeDependenciaGera503ComRetryAfter() {   // CA01.6 (mapeamento)
        when(cadastrar.cadastrar(any())).thenThrow(new DependenciaIndisponivelException(
                DependenciaIndisponivelException.Dependencia.BANCO, new RuntimeException("senha=xyz")));

        var resultado = post(CORPO_VALIDO, MediaType.APPLICATION_JSON);

        assertErro(resultado, 503, "BANCO_INDISPONIVEL");
        assertThat(resultado.getResponse().getHeader("Retry-After")).isEqualTo("5");
        assertThat(resultado).bodyJson().extractingPath("$.detail").asString().doesNotContain("senha");
    }

    @Test
    void contentTypeIncorretoGera415() {
        assertErro(post(CORPO_VALIDO, MediaType.TEXT_PLAIN), 415, "TIPO_MIDIA_NAO_SUPORTADO");
    }

    @Test
    void listagemUsaPadroesEFiltro() {
        when(consultar.listar(Optional.of(Categoria.CURSO), 0, 20))
                .thenReturn(new Pagina<>(List.of(), 0, 20, 0));

        var resultado = get("/api/conhecimentos?categoria=CURSO");

        assertThat(resultado).hasStatusOk();
        assertThat(resultado).bodyJson().extractingPath("$.totalPaginas").isEqualTo(0);
        verify(consultar).listar(Optional.of(Categoria.CURSO), 0, 20);
    }

    @Test
    void parametrosDeListagemInvalidosGeram400() {
        assertErro(get("/api/conhecimentos?tamanho=500"), 400, "VALIDACAO_FALHOU");
        assertErro(get("/api/conhecimentos?tamanho=0"), 400, "VALIDACAO_FALHOU");
        assertErro(get("/api/conhecimentos?pagina=-1"), 400, "VALIDACAO_FALHOU");
        assertErro(get("/api/conhecimentos?categoria=XYZ"), 400, "VALIDACAO_FALHOU");
        verify(consultar, never()).listar(any(), anyInt(), anyInt());
    }

    @Test
    void consultaPorIdInvalidoGera400() {
        assertErro(get("/api/conhecimentos/nao-e-uuid"), 400, "VALIDACAO_FALHOU");
    }

    @Test
    void consultaPorIdInexistenteGera404() {
        UUID id = UUID.randomUUID();
        when(consultar.buscarPorId(id)).thenThrow(new ConhecimentoNaoEncontradoException(id));

        var resultado = get("/api/conhecimentos/" + id);

        assertErro(resultado, 404, "RECURSO_NAO_ENCONTRADO");
        assertThat(resultado).bodyJson().extractingPath("$.detail").asString().doesNotContain(id.toString());
    }

    private MvcTestResult post(String corpo, MediaType tipo) {
        return mvc.post().uri("/api/conhecimentos").header(AdminTokenFilter.HEADER, TOKEN)
                .contentType(tipo).content(corpo).exchange();
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).header(AdminTokenFilter.HEADER, TOKEN).exchange();
    }

    private static void assertErro(MvcTestResult resultado, int status, String codigo) {
        assertThat(resultado).hasStatus(status);
        assertThat(resultado.getResponse().getContentType()).startsWith("application/problem+json");
        assertThat(resultado).bodyJson().extractingPath("$.codigo").isEqualTo(codigo);
    }

    private static Conhecimento conhecimento() {
        return new Conhecimento(UUID.randomUUID(), "Curso Java Web Developer", "Java 21 e Spring Boot.",
                Categoria.CURSO, "https://www.cotiinformatica.com.br/cursos/java-web-developer", List.of("java"),
                0, 22, Instant.parse("2026-10-25T18:30:00Z"));
    }
}

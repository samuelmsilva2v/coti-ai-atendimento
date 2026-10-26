package br.com.coti.conhecimento.controller.dto;

import br.com.coti.conhecimento.domain.model.Categoria;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CadastrarConhecimentoRequestTest {

    private static jakarta.validation.ValidatorFactory fabrica;
    private static Validator validator;

    @BeforeAll
    static void criar() {
        fabrica = Validation.buildDefaultValidatorFactory();
        validator = fabrica.getValidator();
    }

    @AfterAll
    static void fechar() {
        fabrica.close();
    }

    @Test
    void requisicaoValidaSemTags() {
        assertThat(validar(req("Título", "Conteúdo", Categoria.CURSO, "https://coti", null))).isEmpty();
    }

    @Test
    void camposObrigatorios() {
        assertThat(campos(validar(req(null, null, null, null, null))))
                .containsExactlyInAnyOrder("titulo", "conteudo", "categoria", "fonte");
    }

    @Test
    void textosSoComEspacosSaoInvalidos() {
        assertThat(campos(validar(req("   ", "   ", Categoria.CURSO, "  ", null))))
                .containsExactlyInAnyOrder("titulo", "conteudo", "fonte");
    }

    @Test
    void limitesDeTamanho() {
        assertThat(validar(req("t".repeat(200), "c".repeat(100_000), Categoria.CURSO, "f".repeat(500),
                Collections.nCopies(10, "x".repeat(50))))).isEmpty();

        assertThat(campos(validar(req("t".repeat(201), "c".repeat(100_001), Categoria.CURSO, "f".repeat(501),
                Collections.nCopies(11, "x"))))).containsExactlyInAnyOrder("titulo", "conteudo", "fonte", "tags");
    }

    @Test
    void tagVaziaOuLongaEInvalida() {
        assertThat(campos(validar(req("T", "C", Categoria.CURSO, "F", List.of("ok", " ", "x".repeat(51))))))
                .containsExactlyInAnyOrder("tags[1].<list element>", "tags[2].<list element>");
    }

    private static CadastrarConhecimentoRequest req(String titulo, String conteudo, Categoria categoria, String fonte,
            List<String> tags) {
        return new CadastrarConhecimentoRequest(titulo, conteudo, categoria, fonte, tags);
    }

    private static Set<ConstraintViolation<CadastrarConhecimentoRequest>> validar(CadastrarConhecimentoRequest r) {
        return validator.validate(r);
    }

    private static List<String> campos(Set<ConstraintViolation<CadastrarConhecimentoRequest>> violacoes) {
        return violacoes.stream().map(v -> v.getPropertyPath().toString()).toList();
    }
}

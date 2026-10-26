package br.com.coti.atendimentos.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Art. 4, 5 e 6 / ADR-003, no nível do código (o enforcer cobre as dependências Maven): nenhum acesso a
 * banco, vetor ou embedding, e nenhum cliente HTTP próprio para a api-conhecimento (só MCP).
 */
@AnalyzeClasses(packages = "br.com.coti.atendimentos", importOptions = ImportOption.DoNotIncludeTests.class)
class ProibicoesArchTest {

    @ArchTest
    static final ArchRule semBancoVetorOuEmbedding = noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..", "javax.sql..", "java.sql..", "jakarta.persistence..",
                    "org.springframework.ai.vectorstore..", "org.springframework.ai.embedding..")
            .because("a api-atendimentos não acessa banco nem gera embeddings (Art. 5 e 6)");

    @ArchTest
    static final ArchRule semClienteRestParaAApiDeConhecimento = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestClient")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("org.springframework.web.client.RestTemplate")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.net.http.HttpClient")
            .because("o conhecimento é obtido exclusivamente pela ferramenta MCP (Art. 4)");
}

package br.com.coti.conhecimento.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Regras de camadas de specs/03-architecture.md, seção 4 (ADR-013). */
@AnalyzeClasses(packages = "br.com.coti.conhecimento", importOptions = ImportOption.DoNotIncludeTests.class)
class CamadasArchTest {

    @ArchTest
    static final ArchRule dominioNaoDependeDeFrameworks = noClasses()
            .that().resideInAPackage("..conhecimento.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "java.sql..", "javax.sql..", "jakarta.persistence..",
                    "tools.jackson..", "com.fasterxml.jackson..", "io.modelcontextprotocol..")
            .because("o domínio é puro (Art. 3)");

    @ArchTest
    static final ArchRule dominioNaoDependeDeOutrasCamadas = noClasses()
            .that().resideInAPackage("..conhecimento.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..conhecimento.application..", "..conhecimento.controller..",
                    "..conhecimento.mcp..", "..conhecimento.infrastructure..");

    @ArchTest
    static final ArchRule aplicacaoNaoDependeDeAdaptadores = noClasses()
            .that().resideInAPackage("..conhecimento.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..conhecimento.controller..", "..conhecimento.mcp..", "..conhecimento.infrastructure..",
                    "org.springframework.web..", "org.springframework.jdbc..", "io.modelcontextprotocol..");

    @ArchTest
    static final ArchRule adaptadoresDeEntradaNaoDependemDaInfraestrutura = noClasses()
            .that().resideInAnyPackage("..conhecimento.controller..", "..conhecimento.mcp..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..conhecimento.infrastructure..", "org.springframework.jdbc..", "org.springframework.ai.vectorstore..");

    @ArchTest
    static final ArchRule infraestruturaNaoDependeDeAdaptadoresNemDeCasosDeUso = noClasses()
            .that().resideInAPackage("..conhecimento.infrastructure..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..conhecimento.controller..", "..conhecimento.mcp..", "..conhecimento.application..");
}

package br.com.coti.atendimentos.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Regras de camadas de specs/03-architecture.md, seção 4 (ADR-013). */
@AnalyzeClasses(packages = "br.com.coti.atendimentos", importOptions = ImportOption.DoNotIncludeTests.class)
class CamadasArchTest {

    @ArchTest
    static final ArchRule dominioNaoDependeDeFrameworks = noClasses()
            .that().resideInAPackage("..atendimentos.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "tools.jackson..", "com.fasterxml.jackson..",
                    "io.modelcontextprotocol..", "io.micrometer..")
            .because("o domínio é puro (Art. 3)");

    @ArchTest
    static final ArchRule dominioNaoDependeDeOutrasCamadas = noClasses()
            .that().resideInAPackage("..atendimentos.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..atendimentos.application..", "..atendimentos.controller..", "..atendimentos.infrastructure..");

    @ArchTest
    static final ArchRule aplicacaoNaoDependeDeAdaptadoresNemDeFrameworks = noClasses()
            .that().resideInAPackage("..atendimentos.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..atendimentos.controller..", "..atendimentos.infrastructure..",
                    "org.springframework..", "io.modelcontextprotocol..", "io.micrometer..");

    @ArchTest
    static final ArchRule controllerNaoDependeDaInfraestrutura = noClasses()
            .that().resideInAPackage("..atendimentos.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..atendimentos.infrastructure..", "io.modelcontextprotocol..", "org.springframework.ai..");

    @ArchTest
    static final ArchRule infraestruturaNaoDependeDoController = noClasses()
            .that().resideInAPackage("..atendimentos.infrastructure..")
            .should().dependOnClassesThat().resideInAPackage("..atendimentos.controller..");
}

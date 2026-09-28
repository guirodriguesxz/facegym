package com.facegym;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.facegym", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

    @ArchTest
    static final ArchRule nucleoSemFrameworks = noClasses()
            .that().resideInAnyPackage("com.facegym.domain..", "com.facegym.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "java.sql..", "java.net.http..", "io.github.resilience4j..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule dominioNaoConheceAplicacao = noClasses()
            .that().resideInAPackage("com.facegym.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("com.facegym.application..", "com.facegym.adapters..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule aplicacaoNaoConheceAdaptadores = noClasses()
            .that().resideInAPackage("com.facegym.application..")
            .should().dependOnClassesThat().resideInAPackage("com.facegym.adapters..")
            .allowEmptyShould(true);
}

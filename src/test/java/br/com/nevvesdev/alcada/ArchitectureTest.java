package br.com.nevvesdev.alcada;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.nevvesdev.alcada");

    @Test
    void domainShouldNotDependOnOuterLayersOrFrameworks() {
        noClasses().that().resideInAPackage("br.com.nevvesdev.alcada.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "br.com.nevvesdev.alcada.application..",
                        "br.com.nevvesdev.alcada.infrastructure..",
                        "br.com.nevvesdev.alcada.api..",
                        "org.springframework..",
                        "jakarta..",
                        "javax..",
                        "org.hibernate..",
                        "com.fasterxml..")
                .check(CLASSES);
    }

    @Test
    void applicationShouldNotDependOnAdaptersOrFrameworks() {
        noClasses().that().resideInAPackage("br.com.nevvesdev.alcada.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "br.com.nevvesdev.alcada.infrastructure..",
                        "br.com.nevvesdev.alcada.api..",
                        "org.springframework..",
                        "jakarta..",
                        "javax..",
                        "org.hibernate..",
                        "com.fasterxml..")
                .check(CLASSES);
    }

    @Test
    void domainPackagesShouldBeFreeOfCycles() {
        slices().matching("br.com.nevvesdev.alcada.domain.(*)..")
                .should().beFreeOfCycles()
                .check(CLASSES);
    }

    @Test
    void applicationPackagesShouldBeFreeOfCycles() {
        slices().matching("br.com.nevvesdev.alcada.application.(*)..")
                .should().beFreeOfCycles()
                .check(CLASSES);
    }
}
package br.com.nevvesdev.alcada;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ApiArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.nevvesdev.alcada");

    @Test
    void apiShouldNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("br.com.nevvesdev.alcada.api..")
                .should().dependOnClassesThat().resideInAPackage("br.com.nevvesdev.alcada.infrastructure..")
                .check(CLASSES);
    }

    @Test
    void innerLayersShouldNotDependOnApi() {
        noClasses().that().resideInAnyPackage(
                        "br.com.nevvesdev.alcada.domain..",
                        "br.com.nevvesdev.alcada.application..",
                        "br.com.nevvesdev.alcada.infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("br.com.nevvesdev.alcada.api..")
                .check(CLASSES);
    }
}
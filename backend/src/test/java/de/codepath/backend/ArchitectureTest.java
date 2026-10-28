package de.codepath.backend;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/** Architekturregeln aus dem Plan, als Test geprüft. */
class ArchitectureTest {

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("de.codepath.backend");

    static final List<String> MODULES = List.of("identity", "content", "engine", "progress", "review", "classroom", "report", "platform", "site", "pathway", "transfer");

    @Test
    void repositoriesAreOnlyUsedInsideTheirOwnPackage() {
        for (String module : MODULES) {
            String pkg = "de.codepath.backend." + module + "..";
            ArchRule rule = noClasses().that().resideOutsideOfPackage(pkg)
                    .should().dependOnClassesThat(com.tngtech.archunit.base.DescribedPredicate.and(
                            com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage(pkg),
                            com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo(Repository.class)))
                    .because("andere Pakete greifen nur über die Service-Schnittstelle zu");
            rule.check(CLASSES);
        }
    }

    @Test
    void modulesHaveNoCycles() {
        slices().matching("de.codepath.backend.(*)..").should().beFreeOfCycles().check(CLASSES);
    }

    @Test
    void engineDependsOnNoOtherModule() {
        noClasses().that().resideInAPackage("de.codepath.backend.engine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "de.codepath.backend.identity..", "de.codepath.backend.content..", "de.codepath.backend.progress..",
                        "de.codepath.backend.review..", "de.codepath.backend.classroom..", "de.codepath.backend.report..",
                        "de.codepath.backend.site..", "de.codepath.backend.pathway..", "de.codepath.backend.transfer..")
                .check(CLASSES);
    }
}

package zur.koeln.kickertool.bootstrap;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Schützt die hexagonale Struktur: Abhängigkeiten zeigen immer nach innen, Adapter kennen sich nicht. */
@AnalyzeClasses(packages = "zur.koeln.kickertool", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDependsOnNothingOutsideTheJdk = classes()
            .that().resideInAPackage("..kickertool.domain..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("..kickertool.domain..", "java..")
            .as("Die Domäne hängt von keinem Framework und keiner anderen Schicht ab");

    @ArchTest
    static final ArchRule applicationOnlyKnowsDomainAndTransactionAnnotation = classes()
            .that().resideInAPackage("..kickertool.application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "..kickertool.application..", "..kickertool.domain..", "java..",
                    "org.springframework.transaction.annotation..")
            .as("Die Anwendungsschicht kennt nur die Domäne und @Transactional, keine Adapter und kein Framework");

    @ArchTest
    static final ArchRule adaptersTalkToPortsNotToServices = noClasses()
            .that().resideInAPackage("..kickertool.adapter..")
            .should().dependOnClassesThat().resideInAPackage("..kickertool.application.service..")
            .as("Adapter sprechen nur mit den Ports der Anwendungsschicht, nicht mit deren Implementierungen");

    @ArchTest
    static final ArchRule adaptersDoNotDependOnEachOther = slices()
            .matching("..kickertool.adapter.(*).(*)..")
            .should().notDependOnEachOther()
            .as("REST, Persistenz und Events kennen sich nicht gegenseitig");

    @ArchTest
    static final ArchRule nothingDependsOnTheBootstrapModule = noClasses()
            .that().resideOutsideOfPackage("..kickertool.bootstrap..")
            .should().dependOnClassesThat().resideInAPackage("..kickertool.bootstrap..")
            .as("Nur das Bootstrap-Modul darf alles verdrahten");
}

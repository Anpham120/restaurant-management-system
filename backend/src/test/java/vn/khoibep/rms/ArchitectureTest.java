package vn.khoibep.rms;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * The layout of every module (doc 09, section 9.3): each kind of class in the sub-package of its layer, and calls
 * only going down the layers, controller → service → repository → entity. A class put elsewhere fails the build.
 * common and config are shared and have their own layout.
 */
@AnalyzeClasses(packages = "vn.khoibep.rms", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // Where each kind of class lives.

    @ArchTest
    static final ArchRule controllersLiveInController = classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule servicesLiveInService = classes().that().areAnnotatedWith(Service.class)
            .and().resideOutsideOfPackage("..config..")
            .should().resideInAPackage("..service..");

    @ArchTest
    static final ArchRule repositoriesLiveInRepository = classes().that().areAssignableTo(Repository.class)
            .should().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule entitiesLiveInEntity = classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..entity..");

    @ArchTest
    static final ArchRule enumsLiveInEnums = classes().that().areEnums().and().areTopLevelClasses()
            .and().resideOutsideOfPackages("..common..", "..config..")
            .should().resideInAPackage("..enums..");

    @ArchTest
    static final ArchRule dtosLiveInDto = classes().that().haveSimpleNameEndingWith("Dtos")
            .should().resideInAPackage("..dto..");

    // Calls go down the layers only.

    @ArchTest
    static final ArchRule controllersGoThroughServices = noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule nothingCallsControllers = noClasses().that().resideOutsideOfPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule dataDoesNotCallServices = noClasses()
            .that().resideInAnyPackage("..repository..", "..entity..", "..enums..")
            .should().dependOnClassesThat().resideInAPackage("..service..");

    /** Entities and enums are the data model: they do not know how the API shapes its requests and answers. */
    @ArchTest
    static final ArchRule dataDoesNotKnowTheApi = noClasses().that().resideInAnyPackage("..entity..", "..enums..")
            .should().dependOnClassesThat().resideInAPackage("..dto..");
}

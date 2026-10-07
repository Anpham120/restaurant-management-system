package vn.khoibep.rms;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * The layered layout of the backend (doc 09, section 9.3, P3-06): each kind of class in the package of its layer,
 * shared by every feature, and calls only going down the layers, controller → service → repository → model. A class
 * put elsewhere fails the build. common and config are shared and have their own layout.
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
    static final ArchRule entitiesLiveInModel = classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..model..");

    @ArchTest
    static final ArchRule enumsLiveInEnums = classes().that().areEnums().and().areTopLevelClasses()
            .and().resideOutsideOfPackages("..common..", "..config..")
            .should().resideInAPackage("..enums..");

    @ArchTest
    static final ArchRule dtosLiveInDto = classes().that().haveSimpleNameEndingWith("Dtos")
            .should().resideInAPackage("..dto..");

    @ArchTest
    static final ArchRule aspectsLiveInAspect = classes().that().areAnnotatedWith(Aspect.class)
            .should().resideInAPackage("..aspect..");

    // Calls go down the layers only.

    @ArchTest
    static final ArchRule controllersGoThroughServices = noClasses().that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..repository..");

    @ArchTest
    static final ArchRule nothingCallsControllers = noClasses().that().resideOutsideOfPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule dataDoesNotCallServices = noClasses()
            .that().resideInAnyPackage("..repository..", "..model..", "..enums..")
            .should().dependOnClassesThat().resideInAPackage("..service..");

    /** The model and the enums are the data: they do not know how the API shapes its requests and answers. */
    @ArchTest
    static final ArchRule dataDoesNotKnowTheApi = noClasses().that().resideInAnyPackage("..model..", "..enums..")
            .should().dependOnClassesThat().resideInAPackage("..dto..");
}

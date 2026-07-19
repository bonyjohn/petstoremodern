package com.petstore.core.arch;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.core.domain.JavaClasses;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Skeleton rules guarding the module boundaries: catalog, customer, order and
 * notification are independent business modules that may depend on common but
 * not on each other's internals.
 */
class ModuleBoundariesTest {

	private static final JavaClasses CLASSES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("com.petstore.core");

	@Test
	void applicationCodeDoesNotDependOnMigration() {
		ArchRule rule = noClasses().that().resideOutsideOfPackage("com.petstore.core.migration..").should()
				.dependOnClassesThat().resideInAPackage("com.petstore.core.migration..");
		rule.check(CLASSES);
	}

	@Test
	void businessModulesAreFreeOfCycles() {
		ArchRule rule = slices().matching("com.petstore.core.(*)..").should().beFreeOfCycles();
		rule.check(CLASSES);
	}

	@Test
	void catalogShouldNotDependOnOtherBusinessModules() {
		ArchRule rule = classes().that().resideInAPackage("com.petstore.core.catalog..").should()
				.onlyDependOnClassesThat().resideOutsideOfPackages("com.petstore.core.customer..",
						"com.petstore.core.order..", "com.petstore.core.notification..");
		rule.check(CLASSES);
	}

	@Test
	void customerShouldNotDependOnOtherBusinessModules() {
		ArchRule rule = classes().that().resideInAPackage("com.petstore.core.customer..").should()
				.onlyDependOnClassesThat().resideOutsideOfPackages("com.petstore.core.catalog..",
						"com.petstore.core.order..", "com.petstore.core.notification..");
		rule.check(CLASSES);
	}

	@Test
	void orderShouldNotDependOnOtherBusinessModules() {
		ArchRule rule = classes().that().resideInAPackage("com.petstore.core.order..").should()
				.onlyDependOnClassesThat().resideOutsideOfPackages("com.petstore.core.catalog..",
						"com.petstore.core.customer..", "com.petstore.core.notification..");
		rule.check(CLASSES);
	}

	@Test
	void notificationShouldNotDependOnOtherBusinessModules() {
		ArchRule rule = classes().that().resideInAPackage("com.petstore.core.notification..").should()
				.onlyDependOnClassesThat().resideOutsideOfPackages("com.petstore.core.catalog..",
						"com.petstore.core.customer..", "com.petstore.core.order..");
		rule.check(CLASSES);
	}

}

package cn.starcloud.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

  private final JavaClasses classes = new ClassFileImporter().importPackages("cn.starcloud.core");

  @Test
  void coreHasNoPlatformDependencies() {
    ArchRule rule =
        noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("javafx..", "android..", "java.awt..");
    rule.check(classes);
  }
}

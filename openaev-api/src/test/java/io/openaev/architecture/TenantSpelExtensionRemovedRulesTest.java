package io.openaev.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import io.openaev.architecture.fixture.TenantSpelExtensionFixtures;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves the SpEL extension guard fires on a class that provides an evaluation context extension,
 * directly or through a parent, and stays quiet on any other class. {@link
 * TenantSpelExtensionRemovedArchTest} runs the same rule over production.
 */
@DisplayName("SpEL extension guard: it fires on an extension provider, and only on those")
class TenantSpelExtensionRemovedRulesTest {

  private static final String FIXTURES = TenantSpelExtensionFixtures.class.getName() + "$";

  private static final JavaClasses FIXTURE_CLASSES =
      new ClassFileImporter().importPackagesOf(TenantSpelExtensionFixtures.class);

  @Test
  @DisplayName("given_directAndInheritedProviders_should_flagBothAndNotAPlainClass")
  void given_directAndInheritedProviders_should_flagBothAndNotAPlainClass() {
    // Act
    List<String> flagged =
        TenantSpelExtensionRemovedArchTest.no_class_provides_a_spel_evaluation_context_extension
            .evaluate(FIXTURE_CLASSES)
            .getFailureReport()
            .getDetails()
            .stream()
            .map(detail -> detail.substring(detail.indexOf(FIXTURES) + FIXTURES.length()))
            .map(detail -> detail.replaceAll("[^A-Za-z].*", ""))
            .distinct()
            .sorted()
            .toList();

    // Assert
    assertThat(flagged).containsExactly("ExtendsAnExtensionProvider", "ProvidesAnExtension");
  }
}

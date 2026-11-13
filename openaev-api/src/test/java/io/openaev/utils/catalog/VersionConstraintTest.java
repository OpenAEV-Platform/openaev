package io.openaev.utils.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Platform version constraint")
class VersionConstraintTest {

  @ParameterizedTest(name = "{0} with platform {1}: {2}")
  @CsvSource({
    ">=2.3, 2.3.1, true",
    ">=2.3, 2.2.9, false",
    ">2.3, 2.3.0, false",
    "==2.3.1, 2.3.1, true",
    "~=2.3.0, 2.3.4, true",
    "~=2.3.0, 2.4.0, false",
    "~=2.3, 2.9.0, true",
    "~=2.3, 3.0.0, false",
    ">=2.3.1, 2.3.1-rc1, false",
    "2.3, 1.0.0, true",
    ">=2.3, unknown, true"
  })
  void given_constraintAndPlatformVersion_should_tellWhetherItAllowsIt(
      String constraint, String version, boolean allowed) {
    // Act & Assert
    assertThat(VersionConstraint.allows(constraint, version)).isEqualTo(allowed);
  }
}

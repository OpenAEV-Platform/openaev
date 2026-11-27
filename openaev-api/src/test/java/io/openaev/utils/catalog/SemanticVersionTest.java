package io.openaev.utils.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("Next semantic version")
class SemanticVersionTest {

  @ParameterizedTest(name = "{0} -> major {1}, minor {2}, patch {3}")
  @CsvSource({
    "1.0.0, 2.0.0, 1.1.0, 1.0.1",
    "1.2.3, 2.0.0, 1.3.0, 1.2.4",
    "v1.9.9, 2.0.0, 1.10.0, 1.9.10"
  })
  void given_version_should_bumpEachPartAndResetTheLowerOnes(
      String version, String major, String minor, String patch) {
    // Arrange
    SemanticVersion current = SemanticVersion.parse(version);

    // Act & Assert
    assertThat(current.nextMajor()).isEqualTo(major);
    assertThat(current.nextMinor()).isEqualTo(minor);
    assertThat(current.nextPatch()).isEqualTo(patch);
  }
}

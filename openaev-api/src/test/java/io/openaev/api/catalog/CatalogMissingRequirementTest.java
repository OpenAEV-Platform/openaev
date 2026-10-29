package io.openaev.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.api.catalog.CatalogMissingRequirement.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("What stops a catalogue scenario from being installed")
class CatalogMissingRequirementTest {

  @Test
  @DisplayName("A missing injector, platform version or chaining blocks the install")
  void given_importBreakingRequirement_should_blockInstall() {
    // Act & Assert
    assertThat(Kind.INJECTOR.blocksInstall()).isTrue();
    assertThat(Kind.OPENAEV.blocksInstall()).isTrue();
    assertThat(Kind.CHAINING.blocksInstall()).isTrue();
  }

  @Test
  @DisplayName("A missing executor or edition only stops the run")
  void given_runOnlyRequirement_should_notBlockInstall() {
    // Act & Assert
    assertThat(Kind.EXECUTOR.blocksInstall()).isFalse();
    assertThat(Kind.EDITION.blocksInstall()).isFalse();
  }
}

package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OperationUtilsJpa empty / not_empty")
class OperationUtilsJpaEmptyTest {

  @Test
  @DisplayName("given_type_should_blankChecksApplyOnlyToTextArraysAndLists")
  void given_type_should_blankChecksApplyOnlyToTextArraysAndLists() {
    assertThat(OperationUtilsJpa.canBeBlank(String.class)).isTrue();
    assertThat(OperationUtilsJpa.canBeBlank(String[].class)).isTrue();
    assertThat(OperationUtilsJpa.canBeBlank(List.class)).isTrue();
    assertThat(OperationUtilsJpa.canBeBlank(Boolean.class)).isFalse();
    assertThat(OperationUtilsJpa.canBeBlank(Instant.class)).isFalse();
    assertThat(OperationUtilsJpa.canBeBlank(UUID.class)).isFalse();
    assertThat(OperationUtilsJpa.canBeBlank(Long.class)).isFalse();
  }
}

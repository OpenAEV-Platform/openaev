package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.migration.V6_20261117160000000__Convert_findings_to_identity.TimeOrderedIds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Migration V6_20261117160000000: time-ordered ids")
class ConvertFindingsToIdentityTimeOrderedIdsTest {

  @Nested
  @DisplayName("Ids drawn in a row")
  class DrawnInARow {

    @Test
    @DisplayName("Ids drawn in a row are version 7 UUIDs and increase, within one millisecond too")
    void given_idsDrawnInARow_should_beVersion7AndIncrease() {
      // Arrange
      TimeOrderedIds ids = new TimeOrderedIds();

      // Act
      List<String> drawn = new ArrayList<>();
      for (int i = 0; i < 10_000; i++) {
        drawn.add(ids.next());
      }

      // Assert
      assertThat(drawn).isSortedAccordingTo(String::compareTo).doesNotHaveDuplicates();
      assertThat(drawn)
          .allSatisfy(
              id -> {
                assertThat(UUID.fromString(id).version()).isEqualTo(7);
                assertThat(UUID.fromString(id).variant()).isEqualTo(2);
              });
    }
  }
}

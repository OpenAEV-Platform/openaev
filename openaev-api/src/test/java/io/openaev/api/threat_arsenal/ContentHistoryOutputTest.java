package io.openaev.api.threat_arsenal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.ContentHistory;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadApproval;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("History rows read from the approval history")
class ContentHistoryOutputTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static PayloadApproval automaticApproval(PayloadApproval.ORIGIN origin) {
    PayloadApproval entry = new PayloadApproval();
    entry.setId("approval-1");
    entry.setStatus(Payload.PAYLOAD_APPROVAL_STATUS.APPROVED);
    entry.setOrigin(origin);
    entry.setAutomatic(true);
    return entry;
  }

  @Nested
  @DisplayName("Automatic approvals without a user")
  class AutomaticApprovals {

    @Test
    @DisplayName("a version approved by the upgrade says so, with no user")
    void given_migrationEntry_should_markUpgradeSource() {
      // Arrange
      PayloadApproval entry = automaticApproval(PayloadApproval.ORIGIN.MIGRATION);

      // Act
      Optional<ContentHistoryOutput> row =
          ContentHistoryOutput.fromApproval(entry, "1.0.0", mapper);

      // Assert
      assertThat(row).isPresent();
      assertThat(row.get().action()).isEqualTo(ContentHistory.ACTION.APPROVE);
      assertThat(row.get().automatic()).isTrue();
      assertThat(row.get().userName()).isNull();
      assertThat(row.get().details().path("source").asText()).isEqualTo("upgrade");
    }

    @Test
    @DisplayName("a platform write without a user keeps the platform source")
    void given_systemEntry_should_keepPlatformSource() {
      // Arrange
      PayloadApproval entry = automaticApproval(PayloadApproval.ORIGIN.SYSTEM);

      // Act
      Optional<ContentHistoryOutput> row =
          ContentHistoryOutput.fromApproval(entry, "1.0.0", mapper);

      // Assert
      assertThat(row).isPresent();
      assertThat(row.get().details().path("source").asText()).isEqualTo("platform");
    }
  }
}

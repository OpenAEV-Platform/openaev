package io.openaev.service.payload_approval;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.*;
import io.openaev.helper.ObjectMapperHelper;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Payload version snapshots")
class PayloadExecutableContentTest {

  private static final ObjectMapper MAPPER = ObjectMapperHelper.openAEVJsonMapper();

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.openaev.service.payload_approval.PayloadFingerprintTest#executableChanges")
  @DisplayName(
      "Given an executable change, its snapshot stored as JSON and applied to the active payload should give the edited content")
  void given_executableChange_should_beRestoredFromItsSnapshot(
      String name, Supplier<Payload> base, Consumer<Payload> modification) throws Exception {
    // Arrange
    Payload edited = base.get();
    modification.accept(edited);
    String stored = MAPPER.writeValueAsString(PayloadExecutableContent.of(edited));
    Payload active = base.get();
    String activeFingerprint = PayloadFingerprint.of(active);

    // Act
    PayloadExecutableContent snapshot = MAPPER.readValue(stored, PayloadExecutableContent.class);
    snapshot.applyTo(active, PayloadVersionService.Active.of(edited).file());

    // Assert
    assertThat(PayloadFingerprint.of(active))
        .isEqualTo(PayloadFingerprint.of(edited))
        .isNotEqualTo(activeFingerprint);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("io.openaev.service.payload_approval.PayloadFingerprintTest#executableChanges")
  @DisplayName(
      "Given an edit held back, applying the active snapshot should restore the active content")
  void given_heldBackEdit_should_restoreActiveContent(
      String name, Supplier<Payload> base, Consumer<Payload> modification) {
    // Arrange
    Payload payload = base.get();
    PayloadVersionService.Active active = PayloadVersionService.Active.of(payload);
    modification.accept(payload);

    // Act
    active.content().applyTo(payload, active.file());

    // Assert
    assertThat(PayloadFingerprint.of(payload)).isEqualTo(active.fingerprint());
  }
}

package io.openaev.service.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Command;
import io.openaev.database.model.ContentRevocation;
import io.openaev.database.model.Payload;
import io.openaev.database.repository.ContentRevocationRepository;
import io.openaev.database.repository.PayloadRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayloadVersionServiceTest {

  private static final String EXTERNAL_ID = "payload-external-id";

  @Mock private PayloadRepository payloadRepository;
  @Mock private ContentRevocationRepository contentRevocationRepository;
  @Mock private EntityManager entityManager;
  @InjectMocks private PayloadVersionService payloadVersionService;

  private static Payload payload(String version, Payload.PAYLOAD_STATUS status, boolean latest) {
    Payload payload = new Command();
    payload.setId(UUID.randomUUID().toString());
    payload.setExternalId(EXTERNAL_ID);
    payload.setVersion(version);
    payload.setStatus(status);
    payload.setLatest(latest);
    return payload;
  }

  private static ContentRevocation revocation(String version) {
    ContentRevocation revocation = new ContentRevocation();
    revocation.setType(ContentRevocation.CONTENT_TYPE.PAYLOAD);
    revocation.setExternalId(EXTERNAL_ID);
    revocation.setVersion(version);
    return revocation;
  }

  private void givenRows(List<Payload> payloads, List<ContentRevocation> revocations) {
    when(payloadRepository.findAllByExternalId(EXTERNAL_ID)).thenReturn(payloads);
    when(contentRevocationRepository.findAllByTypeAndExternalIdAndLiftedAtIsNull(
            ContentRevocation.CONTENT_TYPE.PAYLOAD, EXTERNAL_ID))
        .thenReturn(revocations);
  }

  @Nested
  @DisplayName("Refresh latest flag")
  class RefreshLatest {

    @Test
    void given_severalVersions_should_flagHighestSemverOnly() {
      // Arrange
      Payload v1 = payload("1.9.0", Payload.PAYLOAD_STATUS.VERIFIED, true);
      Payload v2 = payload("1.10.0", Payload.PAYLOAD_STATUS.VERIFIED, false);
      givenRows(List.of(v1, v2), List.of());

      // Act
      payloadVersionService.refreshLatest(EXTERNAL_ID);

      // Assert
      assertThat(v1.isLatest()).isFalse();
      assertThat(v2.isLatest()).isTrue();
    }

    @Test
    void given_newLatest_should_clearOldFlagBeforeSettingNewOne() {
      // Arrange
      Payload v1 = payload("1.0.0", Payload.PAYLOAD_STATUS.VERIFIED, true);
      Payload v2 = payload("2.0.0", Payload.PAYLOAD_STATUS.VERIFIED, false);
      givenRows(List.of(v1, v2), List.of());

      // Act
      payloadVersionService.refreshLatest(EXTERNAL_ID);

      // Assert
      InOrder order = inOrder(payloadRepository, entityManager);
      order.verify(payloadRepository).saveAll(List.of(v1));
      order.verify(entityManager).flush();
      order.verify(payloadRepository).save(v2);
    }

    @Test
    void given_latestRevoked_should_flagPreviousVersion() {
      // Arrange
      Payload v1 = payload("1.0.0", Payload.PAYLOAD_STATUS.VERIFIED, false);
      Payload v2 = payload("2.0.0", Payload.PAYLOAD_STATUS.VERIFIED, true);
      givenRows(List.of(v1, v2), List.of(revocation("2.0.0")));

      // Act & Assert
      assertThat(payloadVersionService.refreshLatest(EXTERNAL_ID)).contains(v1);
      assertThat(v2.isLatest()).isFalse();
    }

    @Test
    void given_everyVersionRevoked_should_flagNone() {
      // Arrange
      Payload v1 = payload("1.0.0", Payload.PAYLOAD_STATUS.REVOKED, true);
      givenRows(List.of(v1), List.of(revocation("1.0.0")));

      // Act & Assert
      assertThat(payloadVersionService.refreshLatest(EXTERNAL_ID)).isEmpty();
      assertThat(v1.isLatest()).isFalse();
    }

    @Test
    void given_onlyUnversionedRow_should_keepItLatest() {
      // Arrange
      Payload legacy = payload(null, Payload.PAYLOAD_STATUS.VERIFIED, true);
      givenRows(List.of(legacy), List.of());

      // Act & Assert
      assertThat(payloadVersionService.refreshLatest(EXTERNAL_ID)).contains(legacy);
      assertThat(legacy.isLatest()).isTrue();
    }
  }
}

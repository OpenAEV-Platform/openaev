package io.openaev.service.payload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Command;
import io.openaev.database.model.ContentRevocation;
import io.openaev.database.model.ContentType;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadIdentity;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ContentRevocationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.PayloadIdentityRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.ManagedPayloadReadOnlyException;
import io.openaev.rest.exception.PayloadInUseException;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PayloadVersionServiceTest {

  private static final String EXTERNAL_ID = "payload-external-id";
  private static final String REPOSITORY = "filigran/payloads";
  private static final String TENANT_ID = "tenant-id";

  @Mock private PayloadRepository payloadRepository;
  @Mock private PayloadIdentityRepository payloadIdentityRepository;
  @Mock private ContentRevocationRepository contentRevocationRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private StepRepository stepRepository;
  @Mock private EntityManager entityManager;
  @InjectMocks private PayloadVersionService payloadVersionService;

  private static Tenant tenant() {
    Tenant tenant = new Tenant();
    tenant.setId(TENANT_ID);
    return tenant;
  }

  private static PayloadIdentity managedIdentity() {
    PayloadIdentity identity = localIdentity();
    identity.setManaged(true);
    identity.setRepository(REPOSITORY);
    return identity;
  }

  private static PayloadIdentity localIdentity() {
    PayloadIdentity identity = new PayloadIdentity();
    identity.setId(UUID.randomUUID().toString());
    identity.setExternalId(EXTERNAL_ID);
    identity.setTenant(tenant());
    return identity;
  }

  private static Payload payload(PayloadIdentity identity, String version) {
    Payload payload = new Command();
    payload.setId(UUID.randomUUID().toString());
    payload.setTenant(identity.getTenant());
    payload.setIdentity(identity);
    payload.setVersion(version);
    payload.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    return payload;
  }

  private static ContentRevocation listRevocation(String repository, String version) {
    ContentRevocation revocation = new ContentRevocation();
    revocation.setType(ContentType.PAYLOAD);
    revocation.setRepository(repository);
    revocation.setExternalId(EXTERNAL_ID);
    revocation.setVersion(version);
    return revocation;
  }

  private void givenVersions(PayloadIdentity identity, List<Payload> versions) {
    when(payloadRepository.findAllByIdentityId(identity.getId())).thenReturn(versions);
  }

  private void givenListRevocations(List<ContentRevocation> revocations) {
    lenient()
        .when(
            contentRevocationRepository
                .findAllByTypeAndExternalIdInAndTenantIsNullAndLiftedAtIsNull(
                    eq(ContentType.PAYLOAD), anyCollection()))
        .thenReturn(revocations);
  }

  @Nested
  @DisplayName("Latest pointer")
  class RefreshLatest {

    @Test
    void given_severalVersions_should_pointToHighestSemver() {
      // Arrange
      PayloadIdentity identity = managedIdentity();
      Payload v1 = payload(identity, "1.9.0");
      Payload v2 = payload(identity, "1.10.0");
      givenVersions(identity, List.of(v1, v2));
      givenListRevocations(List.of());

      // Act
      payloadVersionService.refreshLatest(identity);

      // Assert
      assertThat(identity.getLatestPayloadId()).isEqualTo(v2.getId());
      assertThat(v2.isLatest()).isTrue();
      assertThat(v1.isLatest()).isFalse();
    }

    @Test
    void given_latestRevokedByItsRepositoryList_should_pointToPreviousVersion() {
      // Arrange
      PayloadIdentity identity = managedIdentity();
      Payload v1 = payload(identity, "1.0.0");
      Payload v2 = payload(identity, "2.0.0");
      givenVersions(identity, List.of(v1, v2));
      givenListRevocations(List.of(listRevocation(REPOSITORY, "2.0.0")));

      // Act & Assert
      assertThat(payloadVersionService.refreshLatest(identity)).contains(v1);
    }

    @Test
    void given_revocationFromAnotherRepository_should_ignoreIt() {
      // Arrange
      PayloadIdentity identity = managedIdentity();
      Payload v1 = payload(identity, "1.0.0");
      givenVersions(identity, List.of(v1));
      givenListRevocations(List.of(listRevocation("another/repository", "1.0.0")));

      // Act & Assert
      assertThat(payloadVersionService.refreshLatest(identity)).contains(v1);
    }

    @Test
    void given_latestRevokedByTenant_should_pointToPreviousVersion() {
      // Arrange
      PayloadIdentity identity = localIdentity();
      Payload v1 = payload(identity, "1.0.0");
      Payload v2 = payload(identity, "1.1.0");
      givenVersions(identity, List.of(v1, v2));
      when(contentRevocationRepository.findActivePayloadIds(anyCollection(), eq(TENANT_ID)))
          .thenReturn(List.of(v2.getId()));

      // Act & Assert
      assertThat(payloadVersionService.refreshLatest(identity)).contains(v1);
    }

    @Test
    void given_everyVersionRevoked_should_pointToNone() {
      // Arrange
      PayloadIdentity identity = managedIdentity();
      Payload v1 = payload(identity, "1.0.0");
      identity.setLatestPayloadId(v1.getId());
      givenVersions(identity, List.of(v1));
      givenListRevocations(List.of(listRevocation(REPOSITORY, "1.0.0")));

      // Act & Assert
      assertThat(payloadVersionService.refreshLatest(identity)).isEmpty();
      assertThat(identity.getLatestPayloadId()).isNull();
    }

    @Test
    void given_uninstalledIdentity_should_neverReportLatest() {
      // Arrange
      PayloadIdentity identity = managedIdentity();
      Payload v1 = payload(identity, "1.0.0");
      identity.setLatestPayloadId(v1.getId());

      // Act
      identity.setUninstalled(true);

      // Assert
      assertThat(v1.isLatest()).isFalse();
    }
  }

  @Nested
  @DisplayName("Deletion rules (F26)")
  class Deletion {

    @Test
    void given_managedVersion_should_refuseDeletion() {
      // Arrange
      Payload managed = payload(managedIdentity(), "1.0.0");

      // Act & Assert
      assertThrows(
          ManagedPayloadReadOnlyException.class,
          () -> payloadVersionService.assertDeletable(managed));
    }

    @Test
    void given_localVersionRunByInjects_should_refuseAndListWhere() {
      // Arrange
      Payload local = payload(localIdentity(), "1.0.0");
      when(payloadRepository.findUsageNamesByPayloadId(local.getId()))
          .thenReturn(Arrays.asList("Ransomware scenario", null));

      // Act
      PayloadInUseException error =
          assertThrows(
              PayloadInUseException.class, () -> payloadVersionService.assertDeletable(local));

      // Assert
      assertThat(error.getMessage()).contains("Ransomware scenario", "atomic testing");
    }

    @Test
    void given_unusedLocalVersion_should_allowDeletion() {
      // Arrange
      Payload local = payload(localIdentity(), "1.0.0");
      when(payloadRepository.findUsageNamesByPayloadId(local.getId())).thenReturn(List.of());

      // Act & Assert
      assertDoesNotThrow(() -> payloadVersionService.assertDeletable(local));
    }
  }

  @Nested
  @DisplayName("Link candidates for an import (F4)")
  class LinkCandidates {

    @Test
    void given_oneManagedVersionWithSameIdAndContent_should_preferItThenLocalCopies() {
      // Arrange
      Payload managed = payload(managedIdentity(), "2.0.0");
      PayloadIdentity otherId = managedIdentity();
      otherId.setExternalId("another-id");
      Payload other = payload(otherId, "3.0.0");
      Payload copy = payload(localIdentity(), "1.0.0");
      when(payloadRepository.findAllByContentHash("hash", TENANT_ID))
          .thenReturn(List.of(copy, other, managed));

      // Act & Assert
      assertThat(payloadVersionService.findLinkCandidates(EXTERNAL_ID, "hash", TENANT_ID))
          .containsExactly(managed, copy);
    }

    @Test
    void given_twoRepositoriesWithSameIdAndContent_should_linkLocalCopiesOnly() {
      // Arrange
      Payload fromFiligran = payload(managedIdentity(), "1.0.0");
      PayloadIdentity otherRepository = managedIdentity();
      otherRepository.setRepository("another/repository");
      Payload fromOther = payload(otherRepository, "1.0.0");
      Payload copy = payload(localIdentity(), "1.0.0");
      when(payloadRepository.findAllByContentHash("hash", TENANT_ID))
          .thenReturn(List.of(fromFiligran, fromOther, copy));

      // Act & Assert
      assertThat(payloadVersionService.findLinkCandidates(EXTERNAL_ID, "hash", TENANT_ID))
          .containsExactly(copy);
    }
  }

  @Nested
  @DisplayName("Promotion of a local copy (RFC 0005 4.2)")
  class Promotion {

    @Test
    void given_singleCopyAloneInItsIdentity_should_promoteIt() {
      // Arrange
      PayloadIdentity local = localIdentity();
      Payload copy = payload(local, "1.0.0");
      when(payloadRepository.findAllByContentHash("hash", TENANT_ID)).thenReturn(List.of(copy));
      givenVersions(local, List.of(copy));

      // Act
      PayloadVersionService.Promotion promotion =
          payloadVersionService.findPromotion(EXTERNAL_ID, "hash", TENANT_ID);

      // Assert
      assertThat(promotion.promotable()).contains(copy);
      assertThat(promotion.copiesToRepoint()).isEmpty();
    }

    @Test
    void given_copyWithSeveralVersions_should_repointInsteadOfPromote() {
      // Arrange
      PayloadIdentity local = localIdentity();
      Payload copy = payload(local, "1.1.0");
      when(payloadRepository.findAllByContentHash("hash", TENANT_ID)).thenReturn(List.of(copy));
      givenVersions(local, List.of(payload(local, "1.0.0"), copy));

      // Act
      PayloadVersionService.Promotion promotion =
          payloadVersionService.findPromotion(EXTERNAL_ID, "hash", TENANT_ID);

      // Assert
      assertThat(promotion.promotable()).isEmpty();
      assertThat(promotion.copiesToRepoint()).containsExactly(copy);
    }

    @Test
    void given_twoCandidateCopies_should_repointBoth() {
      // Arrange
      Payload first = payload(localIdentity(), "1.0.0");
      Payload second = payload(localIdentity(), "1.0.0");
      when(payloadRepository.findAllByContentHash("hash", TENANT_ID))
          .thenReturn(List.of(first, second));

      // Act
      PayloadVersionService.Promotion promotion =
          payloadVersionService.findPromotion(EXTERNAL_ID, "hash", TENANT_ID);

      // Assert
      assertThat(promotion.promotable()).isEmpty();
      assertThat(promotion.copiesToRepoint()).containsExactlyInAnyOrder(first, second);
    }
  }
}

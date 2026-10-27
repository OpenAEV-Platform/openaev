package io.openaev.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import io.openaev.IntegrationTest;
import io.openaev.api.catalog.RevocationList;
import io.openaev.api.catalog.RevocationListOutput;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Payload;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.rest.payload.service.PayloadCreationService;
import io.openaev.service.payload.PayloadRevocationService;
import io.openaev.utils.fixtures.PayloadInputFixture;
import io.openaev.utils.helpers.PayloadVersionTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Revocation list of a content repository (ADR-011, point 5)")
class RevocationListServiceTest extends IntegrationTest {

  private static final String REPOSITORY = "filigran/openaev-payloads";

  @Autowired private RevocationListService revocationListService;
  @Autowired private PayloadRevocationService payloadRevocationService;
  @Autowired private PayloadCreationService payloadCreationService;
  @Autowired private PayloadVersionTestHelper payloadVersionTestHelper;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private ManagerFactory managerFactory;

  @BeforeEach
  void registerPayloadInjector() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    managerFactory.getManager(TenantContext.getCurrentTenant()).monitorIntegrations();
  }

  private Payload catalogVersion(String externalId, String version) {
    PayloadCreateInput input =
        PayloadInputFixture.createDefaultPayloadCreateInputForCommandLine(List.of());
    input.setName("Revocation list " + UUID.randomUUID());
    Payload created =
        payloadCreationService
            .createPayload(TxCtx.forTenant(TenantContext.getCurrentTenant()), input)
            .payload();
    return payloadVersionTestHelper.asManagedVersion(created, externalId, version);
  }

  private static RevocationList list(
      String repository, long serial, Instant expiresAt, RevocationList.Entry... entries) {
    return new RevocationList(repository, serial, Instant.now(), expiresAt, List.of(entries));
  }

  @Test
  @DisplayName("A list with an expiry date revokes its versions and keeps the date")
  @WithMockUser(isAdmin = true)
  void given_listWithExpiry_should_revokeAndStoreExpiry() {
    // Arrange
    String externalId = "revocation-list-" + UUID.randomUUID();
    Payload payload = catalogVersion(externalId, "1.0.0");
    Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    // Act
    RevocationListOutput output =
        revocationListService.apply(
            list(
                REPOSITORY,
                10,
                expiresAt,
                new RevocationList.Entry("payload", externalId, "1.0.0", "Unsafe")));

    // Assert
    assertThat(output.applied()).isTrue();
    assertThat(output.added()).isEqualTo(1);
    assertThat(output.expiresAt()).isEqualTo(expiresAt);
    assertThat(output.downloadedAt()).isNotNull();
    assertThat(payloadRevocationService.isRevoked(payload)).isTrue();
  }

  @Test
  @DisplayName("A list without an expiry date is applied; a version it no longer lists is lifted")
  @WithMockUser(isAdmin = true)
  void given_listWithoutExpiry_should_applyAndLiftUnlisted() {
    // Arrange
    String externalId = "revocation-list-" + UUID.randomUUID();
    Payload payload = catalogVersion(externalId, "1.0.0");
    revocationListService.apply(
        list(
            REPOSITORY,
            20,
            null,
            new RevocationList.Entry("payload", externalId, "1.0.0", "Unsafe")));

    // Act
    RevocationListOutput output = revocationListService.apply(list(REPOSITORY, 21, null));

    // Assert
    assertThat(output.applied()).isTrue();
    assertThat(output.lifted()).isEqualTo(1);
    assertThat(output.expiresAt()).isNull();
    assertThat(payloadRevocationService.isRevoked(payload)).isFalse();
  }

  @Test
  @DisplayName("An older serial is refused and the same serial changes nothing")
  @WithMockUser(isAdmin = true)
  void given_olderSerial_should_refuse() {
    // Arrange
    String repository = "test/revocations-" + UUID.randomUUID();
    revocationListService.apply(list(repository, 5, null));

    // Act
    RevocationListOutput same = revocationListService.apply(list(repository, 5, null));

    // Assert
    assertThat(same.applied()).isFalse();
    assertThrows(
        BadRequestException.class, () -> revocationListService.apply(list(repository, 4, null)));
    assertThat(revocationListService.findAll())
        .filteredOn(state -> state.repository().equals(repository))
        .singleElement()
        .extracting(RevocationListOutput::serial)
        .isEqualTo(5L);
  }
}

package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.SecurityCoverageHuntValidation;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.SecurityCoverageHuntValidationRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.opencti.client.mutations.ValidateHuntFromEmulation;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorUnavailableError;
import io.openaev.scheduler.jobs.SecurityCoverageHuntValidationJob;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationOutcome;
import io.openaev.service.stix.SecurityCoverageHuntValidationService.HuntValidationRequest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code security_coverage_hunt_validations} on multi-tenancy v2: a tenant scope reads and writes
 * only its own rows, an unscoped access reads nothing, and the delivery job sends every tenant's
 * validations through that tenant's own OpenCTI connection.
 *
 * <p>Not {@code @Transactional}: the primitive refuses to open inside an active transaction. Rows
 * are committed per tenant and removed in {@link #cleanUp()}; ground truth is read with raw JDBC,
 * which the statement inspector does not rewrite.
 */
@TestPropertySource(
    properties = {
      "openaev.tenant.active-tables=security_coverage_hunt_validations",
      "openaev.security-coverage.hunt-validation.enabled=true"
    })
@WithMockUser(isAdmin = true)
@DisplayName("security_coverage_hunt_validations v2 tenant scope")
class SecurityCoverageHuntValidationTenantScopeTest extends IntegrationTest {

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private SecurityCoverageHuntValidationService huntValidationService;
  @Autowired private SecurityCoverageHuntValidationRepository huntValidationRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private SecurityCoverageHuntValidationJob huntValidationJob;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private OpenCTIConnectorService openCTIConnectorService;

  private String tenantA;
  private String tenantB;

  @AfterEach
  void cleanUp() {
    if (tenantA != null || tenantB != null) {
      jdbcTemplate.update(
          "DELETE FROM security_coverage_hunt_validations WHERE tenant_id IN (?, ?)",
          tenantA,
          tenantB);
      jdbcTemplate.update("DELETE FROM injects WHERE tenant_id IN (?, ?)", tenantA, tenantB);
      jdbcTemplate.update("DELETE FROM assets WHERE tenant_id IN (?, ?)", tenantA, tenantB);
      tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    }
    TenantContext.clearCurrentTenant();
  }

  /** One planned validation per tenant, with the inject and platform its foreign keys need. */
  private Map<String, SecurityCoverageHuntValidation> seedOneValidationPerTenant()
      throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("hunt-validation-tenant-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("hunt-validation-tenant-b").getId();
    return Map.of(tenantA, seedValidation(tenantA), tenantB, seedValidation(tenantB));
  }

  private SecurityCoverageHuntValidation seedValidation(String tenantId) {
    return inTenant(
        tenantId,
        () -> {
          Inject inject = InjectFixture.getDefaultInject();
          inject.setTenant(new Tenant(tenantId));
          injectRepository.save(inject);
          SecurityPlatform platform =
              securityPlatformRepository.save(
                  SecurityPlatformFixture.createDefault(
                      "SIEM " + tenantId, SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM.name()));
          SecurityCoverageHuntValidation validation = new SecurityCoverageHuntValidation();
          validation.setTenant(new Tenant(tenantId));
          validation.setInjectId(inject.getId());
          validation.setTechniqueId("T1059.001");
          validation.setSecurityPlatformId(platform.getId());
          validation.setSecurityPlatformName(platform.getName());
          validation.setCoverageExternalId("security-coverage--" + UUID.randomUUID());
          validation.setWindowStart(Instant.parse("2026-10-03T09:55:00Z"));
          validation.setWindowEnd(Instant.parse("2026-10-03T10:17:00Z"));
          validation.setNextAttemptAt(Instant.now().minusSeconds(60));
          return huntValidationRepository.save(validation);
        });
  }

  private Map<String, Object> rawRow(String validationId) {
    return jdbcTemplate.queryForMap(
        "SELECT tenant_id, security_coverage_hunt_validation_status AS status,"
            + " security_coverage_hunt_validation_attempts AS attempts"
            + " FROM security_coverage_hunt_validations"
            + " WHERE security_coverage_hunt_validation_id = ?",
        validationId);
  }

  private long rawCount(String... validationIds) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM security_coverage_hunt_validations"
            + " WHERE security_coverage_hunt_validation_id IN (?, ?)",
        Long.class,
        (Object[]) validationIds);
  }

  private static Set<String> idsOf(List<HuntValidationRequest> requests, Set<String> among) {
    return requests.stream()
        .map(HuntValidationRequest::id)
        .filter(among::contains)
        .collect(Collectors.toSet());
  }

  private <T> T inTenant(String tenantId, Supplier<T> work) {
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }

  @Nested
  @DisplayName("Isolation")
  class Isolation {

    @Test
    @DisplayName("given tenant A scope should only see tenant A due validations")
    void given_tenantAScope_should_onlySeeTenantADueValidations() throws Exception {
      // Arrange
      Map<String, SecurityCoverageHuntValidation> seeded = seedOneValidationPerTenant();
      String idA = seeded.get(tenantA).getId();
      String idB = seeded.get(tenantB).getId();

      // Act
      List<HuntValidationRequest> due =
          inTenant(tenantA, () -> huntValidationService.collectDueRequests(Instant.now()));

      // Assert
      assertThat(rawCount(idA, idB)).isEqualTo(2L);
      assertThat(idsOf(due, Set.of(idA, idB))).containsExactly(idA);
    }

    @Test
    @DisplayName("given no scope should read nothing although the rows exist (fail-closed)")
    void given_noScope_should_readNothing() throws Exception {
      // Arrange
      Map<String, SecurityCoverageHuntValidation> seeded = seedOneValidationPerTenant();
      String idA = seeded.get(tenantA).getId();
      String idB = seeded.get(tenantB).getId();

      // Act: a raw TransactionTemplate opens a transaction carrying no TxCtx at all.
      List<HuntValidationRequest> due =
          new TransactionTemplate(transactionManager)
              .execute(status -> huntValidationService.collectDueRequests(Instant.now()));

      // Assert
      assertThat(rawCount(idA, idB)).isEqualTo(2L);
      assertThat(idsOf(due, Set.of(idA, idB))).isEmpty();
    }

    @Test
    @DisplayName("given tenant A scope should not settle a tenant B validation")
    void given_tenantAScope_should_notSettleTenantBValidation() throws Exception {
      // Arrange
      Map<String, SecurityCoverageHuntValidation> seeded = seedOneValidationPerTenant();
      String idB = seeded.get(tenantB).getId();

      // Act
      inTenant(
          tenantA,
          () -> {
            huntValidationService.recordOutcomes(
                List.of(
                    new HuntValidationOutcome(
                        idB, HuntValidationOutcome.Kind.VALIDATED, 1, 1, null, null)),
                Instant.now());
            return null;
          });

      // Assert
      Map<String, Object> rowB = rawRow(idB);
      assertThat(rowB.get("tenant_id")).isEqualTo(tenantB);
      assertThat(rowB.get("status")).isEqualTo("PENDING");
      assertThat(((Number) rowB.get("attempts")).intValue()).isZero();
    }
  }

  @Nested
  @DisplayName("Background delivery")
  class BackgroundDelivery {

    @Test
    @DisplayName("given two tenants should send each validation through its own tenant connection")
    void given_twoTenants_should_sendThroughOwnTenantConnection() throws Exception {
      // Arrange
      Map<String, SecurityCoverageHuntValidation> seeded = seedOneValidationPerTenant();
      SecurityCoverageHuntValidation validationA = seeded.get(tenantA);
      SecurityCoverageHuntValidation validationB = seeded.get(tenantB);
      ValidateHuntFromEmulation.HuntValidation accepted =
          new ValidateHuntFromEmulation.HuntValidation();
      accepted.setHuntsCount(1);
      when(openCTIConnectorService.validateHuntFromEmulation(anyString(), any(), any()))
          .thenReturn(accepted);

      // Act
      huntValidationJob.execute(null);

      // Assert
      verify(openCTIConnectorService)
          .validateHuntFromEmulation(
              eq(tenantA),
              argThat(input -> input.injectId().equals(validationA.getInjectId())),
              any(Duration.class));
      verify(openCTIConnectorService)
          .validateHuntFromEmulation(
              eq(tenantB),
              argThat(input -> input.injectId().equals(validationB.getInjectId())),
              any(Duration.class));
      verify(openCTIConnectorService, never())
          .validateHuntFromEmulation(
              eq(tenantA),
              argThat(input -> input.injectId().equals(validationB.getInjectId())),
              any(Duration.class));
      assertThat(rawRow(validationA.getId()).get("status")).isEqualTo("VALIDATED");
      assertThat(rawRow(validationB.getId()).get("status")).isEqualTo("VALIDATED");
    }

    @Test
    @DisplayName("given a tenant whose connector is not registered should postpone its validations")
    void given_connectorNotRegistered_should_postponeTenantValidations() throws Exception {
      // Arrange
      Map<String, SecurityCoverageHuntValidation> seeded = seedOneValidationPerTenant();
      SecurityCoverageHuntValidation validationA = seeded.get(tenantA);
      SecurityCoverageHuntValidation validationB = seeded.get(tenantB);
      ValidateHuntFromEmulation.HuntValidation accepted =
          new ValidateHuntFromEmulation.HuntValidation();
      accepted.setHuntsCount(1);
      when(openCTIConnectorService.validateHuntFromEmulation(anyString(), any(), any()))
          .thenReturn(accepted);
      when(openCTIConnectorService.validateHuntFromEmulation(eq(tenantB), any(), any()))
          .thenThrow(new ConnectorUnavailableError("connector hasn't registered yet"));
      Instant before = Instant.now();

      // Act
      huntValidationJob.execute(null);

      // Assert
      assertThat(rawRow(validationA.getId()).get("status")).isEqualTo("VALIDATED");
      Map<String, Object> rowB = rawRow(validationB.getId());
      assertThat(rowB.get("status")).isEqualTo("PENDING");
      assertThat(((Number) rowB.get("attempts")).intValue()).isZero();
      Instant nextAttemptB =
          jdbcTemplate
              .queryForObject(
                  "SELECT security_coverage_hunt_validation_next_attempt_at"
                      + " FROM security_coverage_hunt_validations"
                      + " WHERE security_coverage_hunt_validation_id = ?",
                  java.sql.Timestamp.class,
                  validationB.getId())
              .toInstant();
      assertThat(nextAttemptB).isAfter(before);
    }
  }
}

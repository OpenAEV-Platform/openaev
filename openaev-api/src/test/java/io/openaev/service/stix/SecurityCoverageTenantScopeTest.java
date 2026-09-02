package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.database.model.SecurityCoverageSendJob;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.SecurityCoverageRepository;
import io.openaev.database.repository.SecurityCoverageSendJobRepository;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.scheduler.jobs.SecurityCoverageJob;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.stix.objects.Bundle;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.SecurityCoverageFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(properties = "openaev.tenant.active-tables=security_coverages")
@WithMockUser(isAdmin = true)
@DisplayName("security_coverages v2 tenant scope")
class SecurityCoverageTenantScopeTest extends IntegrationTest {

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private SecurityCoverageService securityCoverageService;
  @Autowired private SecurityCoverageRepository securityCoverageRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private SecurityCoverageSendJobRepository securityCoverageSendJobRepository;
  @Autowired private SecurityCoverageJob securityCoverageJob;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private OpenCTIConnectorService openCTIConnectorService;

  private String tenantA;
  private String tenantB;

  @AfterEach
  void cleanUp() {
    if (tenantA != null || tenantB != null) {
      jdbcTemplate.update(
          "DELETE FROM security_coverage_send_job WHERE security_coverage_send_job_simulation IN "
              + "(SELECT exercise_id FROM exercises WHERE tenant_id IN (?, ?))",
          tenantA,
          tenantB);
      jdbcTemplate.update("DELETE FROM exercises WHERE tenant_id IN (?, ?)", tenantA, tenantB);
      jdbcTemplate.update(
          "DELETE FROM security_coverages WHERE tenant_id IN (?, ?)", tenantA, tenantB);
      tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    }
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Isolation")
  class Isolation {

    @Test
    @DisplayName("given tenant A scope should not read tenant B coverage")
    void given_tenantAScope_should_notReadTenantBCoverage() throws Exception {
      // Arrange
      tenantA = tenantHelper.createTenantWithCurrentUser("sec-cov-tenant-a").getId();
      tenantB = tenantHelper.createTenantWithCurrentUser("sec-cov-tenant-b").getId();

      String externalIdA = "security-coverage--" + UUID.randomUUID();
      String externalIdB = "security-coverage--" + UUID.randomUUID();
      SecurityCoverage coverageA = createCoverageForTenant(tenantA, externalIdA);
      SecurityCoverage coverageB = createCoverageForTenant(tenantB, externalIdB);

      Long rawRows =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM security_coverages WHERE security_coverage_id IN (?, ?)",
              Long.class,
              coverageA.getId(),
              coverageB.getId());

      // Act
      SecurityCoverage ownRead =
          inTenant(
              tenantA,
              () ->
                  securityCoverageService.getByExternalIdOrCreateSecurityCoverage(
                      externalIdA, tenantA));
      SecurityCoverage crossTenantRead =
          inTenant(
              tenantA,
              () ->
                  securityCoverageService.getByExternalIdOrCreateSecurityCoverage(
                      externalIdB, tenantB));

      // Assert
      assertThat(rawRows).isEqualTo(2L);
      assertThat(ownRead.getId()).isEqualTo(coverageA.getId());
      assertThat(crossTenantRead.getId()).isNull();
    }
  }

  @Nested
  @DisplayName("Background job scope")
  class BackgroundScope {

    @Test
    @DisplayName("given two tenants should build and push a per-tenant scoped bundle")
    void given_twoTenants_should_buildScopedBundlePerTenant() throws Exception {
      // Arrange
      tenantA = tenantHelper.createTenantWithCurrentUser("sec-cov-job-tenant-a").getId();
      tenantB = tenantHelper.createTenantWithCurrentUser("sec-cov-job-tenant-b").getId();

      String externalIdA = "security-coverage--" + UUID.randomUUID();
      String externalIdB = "security-coverage--" + UUID.randomUUID();

      SecurityCoverage coverageA = createCoverageForTenant(tenantA, externalIdA);
      SecurityCoverage coverageB = createCoverageForTenant(tenantB, externalIdB);
      Exercise exerciseA = createFinishedExerciseForTenant(tenantA, coverageA);
      Exercise exerciseB = createFinishedExerciseForTenant(tenantB, coverageB);
      createPendingJob(exerciseA);
      createPendingJob(exerciseB);

      jdbcTemplate.update(
          "UPDATE security_coverage_send_job SET security_coverage_send_job_updated_at = now() - interval '2 minutes'"
              + " WHERE security_coverage_send_job_simulation IN (?, ?)",
          exerciseA.getId(),
          exerciseB.getId());

      ConnectorBase connector = org.mockito.Mockito.mock(ConnectorBase.class);
      when(connector.isRegistered()).thenReturn(true);
      when(openCTIConnectorService.getConnectorBase(anyString()))
          .thenReturn(Optional.of(connector));

      // Act
      securityCoverageJob.execute(null);

      // Assert
      ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
      ArgumentCaptor<String> tenantCaptor = ArgumentCaptor.forClass(String.class);
      verify(openCTIConnectorService, times(2))
          .pushSecurityCoverageStixBundle(bundleCaptor.capture(), tenantCaptor.capture());

      Map<String, String> bundleJsonByTenant = new HashMap<>();
      for (int i = 0; i < bundleCaptor.getAllValues().size(); i++) {
        bundleJsonByTenant.put(
            tenantCaptor.getAllValues().get(i),
            bundleCaptor.getAllValues().get(i).toStix(objectMapper).toString());
      }

      assertThat(bundleJsonByTenant.get(tenantA)).contains(externalIdA).doesNotContain(externalIdB);
      assertThat(bundleJsonByTenant.get(tenantB)).contains(externalIdB).doesNotContain(externalIdA);
    }
  }

  @Nested
  @DisplayName("Bundle hash uniqueness")
  class BundleHashUniqueness {

    @Test
    @DisplayName("given same bundle hash in two tenants should persist both rows")
    void given_sameBundleHashInTwoTenants_should_persistBothRows() throws Exception {
      // Arrange
      tenantA = tenantHelper.createTenantWithCurrentUser("sec-cov-hash-tenant-a").getId();
      tenantB = tenantHelper.createTenantWithCurrentUser("sec-cov-hash-tenant-b").getId();
      String sharedHash = UUID.randomUUID().toString().replace("-", "");

      // Act
      SecurityCoverage first =
          createCoverageForTenantWithHash(
              tenantA, "security-coverage--" + UUID.randomUUID(), sharedHash);
      SecurityCoverage second =
          createCoverageForTenantWithHash(
              tenantB, "security-coverage--" + UUID.randomUUID(), sharedHash);

      // Assert
      assertThat(first.getId()).isNotBlank();
      assertThat(second.getId()).isNotBlank();
      Integer totalRows =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM security_coverages WHERE security_coverage_bundle_hash_md5 = ?",
              Integer.class,
              sharedHash);
      assertThat(totalRows).isEqualTo(2);
    }

    @Test
    @DisplayName("given duplicate bundle hash in same tenant should fail")
    void given_duplicateBundleHashInSameTenant_should_fail() throws Exception {
      // Arrange
      tenantA = tenantHelper.createTenantWithCurrentUser("sec-cov-hash-duplicate-tenant").getId();
      String sharedHash = UUID.randomUUID().toString().replace("-", "");
      createCoverageForTenantWithHash(
          tenantA, "security-coverage--" + UUID.randomUUID(), sharedHash);

      // Act + Assert
      assertThatThrownBy(
              () ->
                  createCoverageForTenantWithHash(
                      tenantA, "security-coverage--" + UUID.randomUUID(), sharedHash))
          .isInstanceOf(DataIntegrityViolationException.class)
          .hasMessageContaining("security_coverage_bundle_hash_md5");
    }
  }

  @Nested
  @DisplayName(
      "Send-job creation gate requires a scope (shared by ExpectationApi, ChallengeApi, "
          + "SimulationChallengeApi and InjectApi's execution callback)")
  class SendJobCreationGateRequiresScope {

    // ExpectationApi#deleteInjectExpectationResult, ChallengeApi#tryChallenge,
    // SimulationChallengeApi#validateChallenge and InjectApi#injectExecutionCallback all cascade
    // into InjectExpectationService's propagateTechnicalExpectation /
    // propagateHumanResponseExpectation, which calls this exact service method. Proving the gate
    // is scope-dependent here demonstrates why those four entrypoints needed TxCtx: without a
    // scope, exercise.getSecurityCoverage() reads as if the coverage did not exist, and the send
    // job is silently never created (fail-closed, not an error).
    @Autowired private SecurityCoverageSendJobService securityCoverageSendJobService;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("given own tenant scope should see the linked coverage and create the send job")
    void given_ownTenantScope_should_seeLinkedCoverageAndCreateSendJob() throws Exception {
      // Arrange
      tenantA = tenantHelper.createTenantWithCurrentUser("sec-cov-gate-tenant-a").getId();
      SecurityCoverage coverage =
          createCoverageForTenant(tenantA, "security-coverage--" + UUID.randomUUID());
      Exercise exercise = createFinishedExerciseForTenant(tenantA, coverage);

      // Act: re-fetch inside the scope under test so the lazy securityCoverage association
      // resolves within THIS transaction's scope, not the (now closed) seeding transaction's.
      inTenant(
          tenantA,
          () -> {
            Exercise scoped = exerciseRepository.findById(exercise.getId()).orElseThrow();
            securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(
                List.of(scoped));
            return null;
          });

      // Assert
      Long jobCount =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM security_coverage_send_job WHERE security_coverage_send_job_simulation = ?",
              Long.class,
              exercise.getId());
      assertThat(jobCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("given no scope should not see the linked coverage and silently skip the send job")
    void given_noScope_should_notSeeLinkedCoverageAndSkipSendJob() throws Exception {
      // Arrange
      tenantA = tenantHelper.createTenantWithCurrentUser("sec-cov-gate-noscope-tenant").getId();
      SecurityCoverage coverage =
          createCoverageForTenant(tenantA, "security-coverage--" + UUID.randomUUID());
      Exercise exercise = createFinishedExerciseForTenant(tenantA, coverage);

      // Act: a raw TransactionTemplate opens a transaction with no TxCtx argument at all, so
      // TenantScopeTransactionAspect stays inert and app.current_tenants is left empty - exactly
      // the state a @Transactional entrypoint without a TxCtx parameter would leave the read in
      // (the pre-fix state of ExpectationApi/ChallengeApi/SimulationChallengeApi/InjectApi).
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                Exercise scoped = exerciseRepository.findById(exercise.getId()).orElseThrow();
                securityCoverageSendJobService.createOrUpdateCoverageSendJobForSimulationsIfReady(
                    List.of(scoped));
              });

      // Assert
      Long jobCount =
          jdbcTemplate.queryForObject(
              "SELECT count(*) FROM security_coverage_send_job WHERE security_coverage_send_job_simulation = ?",
              Long.class,
              exercise.getId());
      assertThat(jobCount).isEqualTo(0L);
    }
  }

  private SecurityCoverage createCoverageForTenant(String tenantId, String externalId) {
    return inTenant(
        tenantId,
        () -> {
          SecurityCoverage coverage = SecurityCoverageFixture.createDefaultSecurityCoverage();
          coverage.setExternalId(externalId);
          coverage.setExternalUrl("https://opencti.local/coverage/" + externalId);
          coverage.setContent("{\"type\": \"security-coverage\", \"id\": \"" + externalId + "\"}");
          // TenantBaseListener no longer stamps the tenant (removed at v2 go-live): attribution
          // is now explicit, same as the production write path in SecurityCoverageService.
          coverage.setTenant(new Tenant(tenantId));
          return securityCoverageRepository.save(coverage);
        });
  }

  private SecurityCoverage createCoverageForTenantWithHash(
      String tenantId, String externalId, String bundleHashMd5) {
    return inTenant(
        tenantId,
        () -> {
          SecurityCoverage coverage = SecurityCoverageFixture.createDefaultSecurityCoverage();
          coverage.setExternalId(externalId);
          coverage.setExternalUrl("https://opencti.local/coverage/" + externalId);
          coverage.setContent("{\"type\": \"security-coverage\", \"id\": \"" + externalId + "\"}");
          coverage.setBundleHashMd5(bundleHashMd5);
          // TenantBaseListener no longer stamps the tenant (removed at v2 go-live): attribution
          // is now explicit, same as the production write path in SecurityCoverageService.
          coverage.setTenant(new Tenant(tenantId));
          return securityCoverageRepository.save(coverage);
        });
  }

  private Exercise createFinishedExerciseForTenant(String tenantId, SecurityCoverage coverage) {
    return inTenant(
        tenantId,
        () -> {
          Exercise exercise = ExerciseFixture.createFinishedAttackExercise();
          exercise.setSecurityCoverage(coverage);
          return exerciseRepository.save(exercise);
        });
  }

  private SecurityCoverageSendJob createPendingJob(Exercise exercise) {
    return inTenant(
        exercise.getTenant().getId(),
        () -> {
          SecurityCoverageSendJob job = new SecurityCoverageSendJob();
          job.setSimulation(exercise);
          job.setStatus("PENDING");
          return securityCoverageSendJobRepository.save(job);
        });
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
}

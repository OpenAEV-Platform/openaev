package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Scenario;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.database.repository.SecurityCoverageRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit test for the duplicate-tolerant lookup in {@link
 * SecurityCoverageService#getByExternalIdOrCreateSecurityCoverage(String, String)}.
 *
 * <p>This is a plain Mockito test on purpose: since {@code
 * V6_20260729130000000__Dedupe_security_coverages_external_id} added the unique constraint on
 * {@code security_coverage_external_id}, an integration test can no longer persist two rows with
 * the same external id, so the legacy-duplicates path is only reachable with a mocked repository.
 */
@ExtendWith(MockitoExtension.class)
public class SecurityCoverageServiceDuplicateLookupTest {

  private static final String EXTERNAL_ID = "security-coverage--duplicated";
  private static final String TENANT_ID = "tenant-a";

  @Mock private SecurityCoverageRepository securityCoverageRepository;
  @InjectMocks private SecurityCoverageService securityCoverageService;

  private SecurityCoverage coverage(String id, Scenario scenario, Instant updatedAt) {
    SecurityCoverage coverage = new SecurityCoverage();
    coverage.setId(id);
    coverage.setExternalId(EXTERNAL_ID);
    coverage.setScenario(scenario);
    coverage.setUpdatedAt(updatedAt);
    return coverage;
  }

  @Test
  @DisplayName("Given no existing coverage, should return a new transient instance")
  public void given_noExistingCoverage_should_returnNewInstance() {
    when(securityCoverageRepository.findAllByExternalIdAndTenantId(EXTERNAL_ID, TENANT_ID))
        .thenReturn(List.of());

    SecurityCoverage result =
        securityCoverageService.getByExternalIdOrCreateSecurityCoverage(EXTERNAL_ID, TENANT_ID);

    assertThat(result.getId()).isNull();
    assertThat(result.getExternalId()).isNull();
  }

  @Test
  @DisplayName("Given a single coverage, should return it")
  public void given_singleCoverage_should_returnIt() {
    SecurityCoverage only = coverage("id-1", null, Instant.parse("2026-07-01T00:00:00Z"));
    when(securityCoverageRepository.findAllByExternalIdAndTenantId(EXTERNAL_ID, TENANT_ID))
        .thenReturn(List.of(only));

    SecurityCoverage result =
        securityCoverageService.getByExternalIdOrCreateSecurityCoverage(EXTERNAL_ID, TENANT_ID);

    assertThat(result).isSameAs(only);
  }

  @Test
  @DisplayName("Given duplicates, should prefer the row linked to a scenario even if older")
  public void given_duplicates_should_preferScenarioLinkedRowEvenIfOlder() {
    SecurityCoverage withScenario =
        coverage("id-old", new Scenario(), Instant.parse("2026-01-01T00:00:00Z"));
    SecurityCoverage newerWithoutScenario =
        coverage("id-new", null, Instant.parse("2026-07-01T00:00:00Z"));
    when(securityCoverageRepository.findAllByExternalIdAndTenantId(EXTERNAL_ID, TENANT_ID))
        .thenReturn(List.of(newerWithoutScenario, withScenario));

    SecurityCoverage result =
        securityCoverageService.getByExternalIdOrCreateSecurityCoverage(EXTERNAL_ID, TENANT_ID);

    assertThat(result).isSameAs(withScenario);
  }

  @Test
  @DisplayName("Given duplicates without scenario, should prefer the most recently updated row")
  public void given_duplicatesWithoutScenario_should_preferMostRecentlyUpdatedRow() {
    SecurityCoverage older = coverage("id-a", null, Instant.parse("2026-01-01T00:00:00Z"));
    SecurityCoverage newer = coverage("id-b", null, Instant.parse("2026-07-01T00:00:00Z"));
    when(securityCoverageRepository.findAllByExternalIdAndTenantId(EXTERNAL_ID, TENANT_ID))
        .thenReturn(List.of(older, newer));

    SecurityCoverage result =
        securityCoverageService.getByExternalIdOrCreateSecurityCoverage(EXTERNAL_ID, TENANT_ID);

    assertThat(result).isSameAs(newer);
  }

  @Test
  @DisplayName("Given duplicates with null updated timestamps, should rank null updates last")
  public void given_duplicatesWithNullUpdatedAt_should_rankNullUpdatesLast() {
    SecurityCoverage withoutTimestamp = coverage("id-a", null, null);
    SecurityCoverage withTimestamp = coverage("id-b", null, Instant.parse("2026-07-01T00:00:00Z"));
    when(securityCoverageRepository.findAllByExternalIdAndTenantId(EXTERNAL_ID, TENANT_ID))
        .thenReturn(List.of(withoutTimestamp, withTimestamp));

    SecurityCoverage result =
        securityCoverageService.getByExternalIdOrCreateSecurityCoverage(EXTERNAL_ID, TENANT_ID);

    assertThat(result).isSameAs(withTimestamp);
  }

  @Test
  @DisplayName("Given fully tied duplicates, should break the tie deterministically on lowest id")
  public void given_fullyTiedDuplicates_should_breakTieOnLowestId() {
    Instant updatedAt = Instant.parse("2026-07-01T00:00:00Z");
    SecurityCoverage second = coverage("id-b", null, updatedAt);
    SecurityCoverage first = coverage("id-a", null, updatedAt);
    when(securityCoverageRepository.findAllByExternalIdAndTenantId(EXTERNAL_ID, TENANT_ID))
        .thenReturn(List.of(second, first));

    SecurityCoverage result =
        securityCoverageService.getByExternalIdOrCreateSecurityCoverage(EXTERNAL_ID, TENANT_ID);

    assertThat(result).isSameAs(first);
  }
}

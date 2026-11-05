package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.database.model.StixRefToExternalRef;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.SecurityCoverageFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code injectors_contracts} is tenant-active and the manual built-in contract id repeats across
 * tenants (#8485), so a caller resolving it for a parent row it already holds (here, the scenario a
 * security-coverage inject is created on) must stay in that parent's own tenant regardless of the
 * ambient scope. This test arms the production active-tables list (not just {@code
 * injectors_contracts} alone, so the class follows production as it activates further tables) and a
 * scope spanning both tenants that hold a copy of the built-in contract, the shape a bare
 * id/external-id lookup cannot resolve, to pin {@code
 * SecurityCoverageInjectService#createdInjectsForScenarioAndSecurityCoverage}'s resolution to the
 * scenario's own tenant.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName(
    "SecurityCoverageInjectService resolves the built-in contract in the scenario's own tenant")
class SecurityCoverageInjectServiceWriteTenantContractTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private SecurityCoverageInjectService securityCoverageInjectService;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  private String otherTenantId;

  @BeforeEach
  void setUp() throws Exception {
    otherTenantId = tenantHelper.createTenant("sec-cov-inject-write-tenant").getId();
    // The built-in contract exists in both tenants, as it does in production (every tenant gets its
    // own copy at onboarding): this is what makes a bare id/external-id lookup ambiguous once the
    // scope spans both of them, rather than silently resolving one of the two rows.
    builtinConnectorRegistration.register(manualInjectorIntegrationFactory, otherTenantId);
    builtinConnectorRegistration.register(
        manualInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  @DisplayName(
      "given a scope spanning both tenants that hold the built-in contract, should resolve the"
          + " scenario's own tenant's copy and create the inject against it")
  void given_scopeSpanningBothTenantsHoldingTheContract_should_resolveScenarioOwnTenantContract() {
    // -- ARRANGE --
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(otherTenantId));
    scenario = scenarioRepository.save(scenario);

    // A vulnerability ref with no matching internal Vulnerability: createInjectsByVulnerabilities
    // treats it as missing and builds a real placeholder inject against the resolved contract, so
    // the resolution under test is pinned by a persisted row, not only by "did it throw".
    String missingVulnerabilityExternalId = "CVE-" + UUID.randomUUID();
    SecurityCoverage securityCoverage = SecurityCoverageFixture.createDefaultSecurityCoverage();
    securityCoverage.setVulnerabilitiesRefs(
        Set.of(
            new StixRefToExternalRef(
                "vulnerability--" + UUID.randomUUID(), List.of(missingVulnerabilityExternalId))));

    // A background-shaped call: no ambient tenant (falls back to the default). The v2 scope spans
    // BOTH tenants that hold a copy of the built-in contract: a bare id/external-id lookup (the
    // pre-fix code) cannot pick one and throws, while the fix resolves it explicitly in the
    // scenario's own tenant regardless of how many other tenants share the request scope.
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    armScopeOn(otherTenantId + "," + Tenant.DEFAULT_TENANT_UUID);

    // -- ACT / ASSERT --
    Scenario scenarioInOtherTenant = scenario;
    assertThatCode(
            () ->
                securityCoverageInjectService.createdInjectsForScenarioAndSecurityCoverage(
                    TxCtx.forTenant(otherTenantId), scenarioInOtherTenant, securityCoverage))
        .doesNotThrowAnyException();

    // Read the created placeholder inject straight from the database, not through the persistence
    // context: the inject<->contract association is a shared-tenant-key join keyed on the inject's
    // own tenant_id, so re-reading it through Hibernate would silently re-derive whichever copy
    // matches the inject's own tenant rather than proving a row was actually written against the
    // contract resolved for the scenario's tenant.
    Number placeholderInjectCount =
        (Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM injects WHERE inject_scenario = :scenarioId AND"
                        + " inject_injector_contract = :contractId")
                .setParameter("scenarioId", scenario.getId())
                .setParameter("contractId", ManualContract.MANUAL_DEFAULT)
                .getSingleResult();
    assertThat(placeholderInjectCount.longValue())
        .as(
            "a placeholder inject for the missing vulnerability was created against the contract"
                + " resolved for the scenario's own tenant")
        .isEqualTo(1L);

    // The inject's own tenant_id must be the scenario's tenant, not whatever the caller's ambient
    // TenantContext happened to carry: TenantBaseListener only stamps a tenant when none is set
    // explicitly, so an unset tenant here would silently land in the ambient default instead of
    // the scenario's own (non-default) tenant.
    Number placeholderInjectInScenarioTenant =
        (Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM injects WHERE inject_scenario = :scenarioId AND"
                        + " inject_injector_contract = :contractId AND tenant_id = :tenantId")
                .setParameter("scenarioId", scenario.getId())
                .setParameter("contractId", ManualContract.MANUAL_DEFAULT)
                .setParameter("tenantId", otherTenantId)
                .getSingleResult();
    assertThat(placeholderInjectInScenarioTenant.longValue())
        .as(
            "the placeholder inject's own tenant_id is the scenario's tenant ("
                + otherTenantId
                + "), not the caller's ambient default tenant")
        .isEqualTo(1L);
  }

  private void armScopeOn(String tenants) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :tenants, true)")
        .setParameter("tenants", tenants)
        .getSingleResult();
  }
}

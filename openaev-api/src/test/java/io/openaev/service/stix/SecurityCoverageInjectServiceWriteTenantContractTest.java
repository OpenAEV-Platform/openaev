package io.openaev.service.stix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.SecurityCoverageFixture;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code injectors_contracts} is tenant-active and the manual built-in contract id repeats across
 * tenants (#8485). {@code
 * SecurityCoverageInjectService#createdInjectsForScenarioAndSecurityCoverage} used to resolve it
 * through {@code TenantContext.getCurrentTenant()} (the v1 ambient thread-local), which a
 * background-shaped call never sets: it defaults to the platform default tenant regardless of which
 * tenant the transaction is actually scoped to, so the lookup missed the scenario's own built-in
 * contract whenever the two tenants differ.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=injectors_contracts")
@WithMockUser(isAdmin = true)
@DisplayName(
    "SecurityCoverageInjectService resolves the built-in contract in the scenario's own tenant")
class SecurityCoverageInjectServiceWriteTenantContractTest extends IntegrationTest {

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
    // own copy at onboarding): this is what makes the bare-ambient-tenant lookup silently resolve
    // the WRONG tenant's copy instead of failing loudly.
    builtinConnectorRegistration.register(manualInjectorIntegrationFactory, otherTenantId);
    builtinConnectorRegistration.register(
        manualInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  @DisplayName(
      "given a background-shaped call with no ambient tenant, should resolve the scenario's own"
          + " tenant's built-in contract rather than the ambient default's")
  void given_backgroundShapedCallWithNoAmbientTenant_should_resolveScenarioOwnTenantContract() {
    // -- ARRANGE --
    Scenario scenario = ScenarioFixture.getScenario();
    scenario.setTenant(new Tenant(otherTenantId));
    scenario = scenarioRepository.save(scenario);
    SecurityCoverage securityCoverage = SecurityCoverageFixture.createDefaultSecurityCoverage();

    // A background-shaped call: no ambient tenant (falls back to the default), the transaction
    // scoped to the scenario's own tenant only, exactly how a real background caller is supposed to
    // scope itself (multi-tenancy.instructions.md). The scope exclude the default tenant on
    // purpose:
    // if the service fell back to TenantContext, the lookup would be denied outright rather than
    // silently hitting the default tenant's row, which is what pins the regression down.
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    armScopeOn(otherTenantId);

    // -- ACT / ASSERT --
    Scenario scenarioInOtherTenant = scenario;
    assertThatCode(
            () ->
                securityCoverageInjectService.createdInjectsForScenarioAndSecurityCoverage(
                    TxCtx.forTenant(otherTenantId), scenarioInOtherTenant, securityCoverage))
        .doesNotThrowAnyException();
  }

  private void armScopeOn(String tenantId) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :tenants, true)")
        .setParameter("tenants", tenantId)
        .getSingleResult();
  }
}

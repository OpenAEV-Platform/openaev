package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.attackpath.AttackPathExecution;
import io.openaev.database.repository.attackpath.AttackPathExecutionRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.service.attackpath.dto.AttackPathExecutionDetailDTO;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.AttackPatternComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code injectors_contracts} is tenant-active and a contract's external id is not unique
 * platform-wide (#8485: a built-in contract id/external id repeats across tenants), so {@link
 * AttackPathGraphService#executionDetail} must resolve the execution's own injector contract in the
 * execution's own tenant, never in whatever scope the caller's request happens to span.
 *
 * <p>Deliberately NOT relying on the v1 ambient tenant for the contract read: {@code TenantContext}
 * only drives the (removed) Hibernate filter, never the v2 statement inspector.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName(
    "AttackPathGraphService#executionDetail resolves the contract in the execution's own tenant")
class AttackPathGraphServiceExecutionDetailTenantScopeTest extends IntegrationTest {

  private static final String SIM = "SIM-EXEC-DETAIL-TENANT-SCOPE";
  private static final String SHARED_EXTERNAL_ID = "shared-contract-ext";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private AttackPathGraphService graphService;
  @Autowired private AttackPathExecutionRepository executionRepository;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  // The remediation mapping is Enterprise-gated; not exercised here but the service reads the
  // gate unconditionally.
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private Tenant ownTenant;
  private String otherTenantId;
  private String executionId;

  @BeforeEach
  void setUp() throws Exception {
    ownTenant = tenantRepository.save(TenantFixture.getTenant("ap-exec-detail-own"));
    otherTenantId = tenantHelper.createTenant("ap-exec-detail-other").getId();
    // injectors and attack_patterns are v2-active: arm the scope to both tenants for the whole
    // fixture build-up, so the well-known-injector lookup (a plain findAll()) sees rows in
    // either tenant instead of the fail-closed empty result an unarmed scope would give it.
    armScopeOn(ownTenant.getId() + "," + otherTenantId);

    // Two different contracts (different composite ids) sharing the SAME external id across
    // tenants, the shape #8485 describes for built-in contracts: a bare external-id lookup cannot
    // tell them apart once the scope spans both tenants.
    TenantContext.setCurrentTenant(ownTenant.getId());
    InjectorContract ownContract =
        injectorContractComposer
            .forInjectorContract(
                InjectorContractFixture.createDefaultInjectorContractWithExternalId(
                    SHARED_EXTERNAL_ID))
            .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
            .withAttackPattern(
                attackPatternComposer.forAttackPattern(
                    AttackPatternFixture.createAttackPatternsWithExternalId(
                        "T-OWN", ownTenant.getId())))
            .persist()
            .get();

    TenantContext.setCurrentTenant(otherTenantId);
    InjectorContract otherContract =
        InjectorContractFixture.createDefaultInjectorContractWithExternalId(SHARED_EXTERNAL_ID);
    otherContract.setTenant(new Tenant(otherTenantId));
    injectorContractComposer
        .forInjectorContract(otherContract)
        .withInjector(injectorFixture.getWellKnownOaevImplantInjector())
        .withAttackPattern(
            attackPatternComposer.forAttackPattern(
                AttackPatternFixture.createAttackPatternsWithExternalId("T-OTHER", otherTenantId)))
        .persist();
    TenantContext.setCurrentTenant(ownTenant.getId());

    AttackPathExecution e = new AttackPathExecution();
    e.setTenant(ownTenant);
    e.setSimulationId(SIM);
    e.setContractExternalId(ownContract.getExternalId());
    e.setSourceKind("INJECTOR");
    e.setTargetKind("ASSET");
    e.setTargetKey("dc-01");
    e.setExecutedAt(Instant.parse("2026-06-18T08:00:00Z"));
    executionId = executionRepository.save(e).getId();
    entityManager.flush();
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    if (otherTenantId != null) {
      tenantHelper.deleteCommittedTenants(otherTenantId);
    }
  }

  @Test
  @DisplayName(
      "given a scope spanning both tenants that hold a contract under the same external id,"
          + " should resolve the execution's own tenant's copy instead of a non-unique result")
  void given_scopeSpanningBothTenantsHoldingTheExternalId_should_resolveOwnTenantContract() {
    // -- ARRANGE --
    // A request-shaped scope spanning both tenants (e.g. X-Tenant-Ids naming both): the pre-fix
    // bare external-id lookup cannot resolve a unique row once the GUC scope spans both.
    armScopeOn(ownTenant.getId() + "," + otherTenantId);

    // -- ACT / ASSERT --
    assertThatCode(() -> graphService.executionDetail(SIM, executionId)).doesNotThrowAnyException();

    AttackPathExecutionDetailDTO d = graphService.executionDetail(SIM, executionId);
    assertThat(d.attackPatterns())
        .as("the execution's own tenant's contract/technique is resolved, never the other tenant's")
        .extracting("externalId")
        .containsExactly("T-OWN");
  }

  private void armScopeOn(String tenants) {
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :tenants, true)")
        .setParameter("tenants", tenants)
        .getSingleResult();
  }
}

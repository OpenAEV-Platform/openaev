package io.openaev.service;

import static io.openaev.database.model.Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * A grant on a threat arsenal contract is validated in the tenant of the group that holds it. A
 * contract that exists in another tenant of the caller's scope does not make the id valid for a
 * group of this one: its grants are read in the group's tenant, where that id names nothing.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Validating a grant on a contract looks the contract up in the group's tenant")
class GrantServiceContractTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private GrantService grantService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectorFixture injectorFixture;

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given_contractOnlyInAnotherTenantOfTheScope_should_refuseItForTheGroupsTenant")
  void given_contractOnlyInAnotherTenantOfTheScope_should_refuseItForTheGroupsTenant()
      throws Exception {
    // -- ARRANGE --
    String otherTenantId = tenantHelper.createTenantWithCurrentUser("grant-contract-b").getId();
    String previousTenant = TenantContext.getCurrentTenant();
    TenantContext.setCurrentTenant(otherTenantId);
    Injector injector;
    try {
      injector = injectorFixture.getWellKnownOaevImplantInjector();
    } finally {
      TenantContext.setCurrentTenant(previousTenant);
    }
    InjectorContract contractInOtherTenant =
        InjectorContractFixture.createDefaultInjectorContract();
    contractInOtherTenant.setTenant(new Tenant(otherTenantId));
    String contractId =
        injectorContractComposer
            .forInjectorContract(contractInOtherTenant)
            .withInjector(injector)
            .persist()
            .get()
            .getId();
    // A request-shaped scope spanning both tenants, as a caller naming both would carry.
    entityManager
        .createNativeQuery("SELECT set_config('app.current_tenants', :tenants, true)")
        .setParameter("tenants", Tenant.DEFAULT_TENANT_UUID + "," + otherTenantId)
        .getSingleResult();

    // -- ACT / ASSERT --
    assertThatThrownBy(
            () -> grantService.validateResourceIdForGrant(
                    contractId, THREAT_ARSENAL, Tenant.DEFAULT_TENANT_UUID))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatCode(() -> grantService.validateResourceIdForGrant(contractId, THREAT_ARSENAL, otherTenantId))
        .doesNotThrowAnyException();
  }
}

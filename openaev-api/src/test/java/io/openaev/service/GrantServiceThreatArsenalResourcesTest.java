package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Tenant;
import io.openaev.injectors.email.EmailContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.ContractRightsFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The threat arsenal bulk delete names no resource in its access check, so the check resolves the
 * grant resources of an empty contract id on a bare thread, where the contract read has no scope.
 * What makes that read harmless is its result, not its scope: an empty id names no contract in any
 * tenant, and no grant can be created on an empty resource id, so no tenant's row can be read or
 * matched through it.
 */
@DisplayName("the grant resources of a threat arsenal check never reach another tenant's contract")
class GrantServiceThreatArsenalResourcesTest extends IntegrationTest {

  private static final String EMPTY_CONTRACT_ID = "";

  @Autowired private GrantService grantService;

  @Nested
  @DisplayName("an empty contract id, as the bulk delete check passes")
  class EmptyContractId {

    @Test
    @DisplayName("a scope of one tenant resolves to the empty id alone")
    void given_oneTenantScope_should_resolveTheEmptyIdAlone() {
      // Act
      List<String> resources =
          grantService.threatArsenalGrantResources(
              EMPTY_CONTRACT_ID, Set.of(Tenant.DEFAULT_TENANT_UUID));

      // Assert
      assertThat(resources).containsExactly(EMPTY_CONTRACT_ID);
    }

    @Test
    @DisplayName("a scope of several tenants resolves to the empty id alone")
    void given_severalTenantsScope_should_resolveTheEmptyIdAlone() {
      // Act
      List<String> resources =
          grantService.threatArsenalGrantResources(
              EMPTY_CONTRACT_ID, Set.of(Tenant.DEFAULT_TENANT_UUID, "another-tenant"));

      // Assert
      assertThat(resources).containsExactly(EMPTY_CONTRACT_ID);
    }

    @Test
    @DisplayName("no grant can be created on an empty resource id")
    void given_anEmptyResourceId_should_refuseTheGrant() {
      // Act and Assert
      assertThatThrownBy(
              () ->
                  grantService.validateResourceIdForGrant(
                      EMPTY_CONTRACT_ID,
                      Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL,
                      Tenant.DEFAULT_TENANT_UUID))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @WithMockUser
  @DisplayName("a contract id the tenant of the scope holds")
  class HeldContractId {

    @Autowired private TenantIsolationTestHelper tenantHelper;
    @Autowired private TenantScopedTransaction tenantTx;
    @Autowired private DataSource dataSource;
    @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
    @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

    private ContractRightsFixture rights;
    private String tenantId;

    @AfterEach
    void cleanup() {
      if (rights != null) {
        rights.cleanup(null);
      }
      if (tenantId != null) {
        tenantHelper.deleteCommittedTenants(tenantId);
      }
    }

    @Test
    @DisplayName("a scope of that tenant adds the payload identity of its copy to the contract id")
    void given_aContractTheScopedTenantHolds_should_addThePayloadIdentityOfItsCopy()
        throws Exception {
      // Arrange
      rights = new ContractRightsFixture(dataSource);
      tenantId = tenantHelper.createTenant("grant-resources").getId();
      builtinConnectorRegistration.register(manualInjectorIntegrationFactory, tenantId);
      String contractId = "grant-resources-" + UUID.randomUUID();
      rights.cloneManualContract(contractId, tenantId, "grant resources");
      String identityId = rights.withPayloadIdentity(contractId, tenantId);

      // Act
      List<String> resources =
          tenantTx.execute(
              TxCtx.forTenant(tenantId),
              () -> grantService.threatArsenalGrantResources(contractId, Set.of(tenantId)));

      // Assert
      assertThat(resources).containsExactly(contractId, identityId);
    }
  }

  @Nested
  @DisplayName("a contract id a tenant of the scope does not hold")
  class ForeignContractId {

    @Test
    @DisplayName("a scope of one tenant never adds a resource id to the contract id")
    void given_anIdTheTenantDoesNotHold_should_resolveTheContractIdAlone() {
      // Act
      List<String> resources =
          grantService.threatArsenalGrantResources(
              EmailContract.EMAIL_DEFAULT, Set.of("a-tenant-without-contracts"));

      // Assert
      assertThat(resources).containsExactly(EmailContract.EMAIL_DEFAULT);
    }
  }
}

package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.config.TenantUriUtils;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.rest.exception.TenantSingleScopeRequiredException;
import io.openaev.service.PermissionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.ContractRightsFixture;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The access gate of a threat arsenal item reads the contract the grant names in the tenant of the
 * request's scope: one tenant takes the ids (the contract id, its payload identity) from that
 * tenant's copy, several tenants take the contract id alone and leave the handler to refuse the
 * request, and a gate that carries no scope reads the ambient tenant. A contract id repeats across
 * tenants, so a lookup by the id alone would match more than one row.
 *
 * <p>The caller is a non-admin member of the default tenant, of tenant A and of tenant B, with no
 * capability: every access comes from grants held through groups the test names. Deliberately NOT
 * {@code @Transactional}: each HTTP call runs in its own transaction with its own tenant scope.
 */
@DisplayName("Threat arsenal access gate resolves the contract in the request's tenant")
@WithMockUser
class ThreatArsenalGrantLookupScopeTest extends ProductionTablesIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TestUserHolder testUserHolder;
  @Autowired private PermissionService permissionService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  private ContractRightsFixture rights;
  private String userId;
  private String tenantA;
  private String tenantB;

  @BeforeEach
  void setup() throws Exception {
    rights = new ContractRightsFixture(dataSource);
    userId = testUserHolder.get().getId();
    tenantA = tenantHelper.createTenantWithCurrentUser("lookup-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("lookup-b").getId();
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    for (String tenantId : new String[] {tenantA, tenantB, Tenant.DEFAULT_TENANT_UUID}) {
      builtinConnectorRegistration.register(manualInjectorIntegrationFactory, tenantId);
    }
    assertThat(rights.builtinRowsIn(tenantA))
        .as("the built-in contract id exists in tenant A")
        .isEqualTo(1);
    assertThat(rights.builtinRowsIn(tenantB))
        .as("the built-in contract id exists in tenant B")
        .isEqualTo(1);
  }

  @AfterEach
  void cleanup() {
    rights.cleanup(userId);
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  private String grantHolder(String tenantId, String resourceId) {
    String groupId = rights.group(tenantId, userId, Set.of());
    rights.grantObserver(groupId, resourceId);
    testUserHolder.refreshSecurityContext();
    return groupId;
  }

  private String tenantUrl(String tenantId, String id) {
    return TENANT_THREAT_ARSENAL_URL.replace(
            "{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenantId)
        + "/"
        + id;
  }

  /** The check of a stream or a bulk delete: a scope of its own, the rights read in that tenant. */
  private boolean holdsRead(String tenantId, User user, String contract) {
    return permissionService.holdsInTenant(
        tenantId,
        () ->
            permissionService.hasPermissionInTenant(
                TxCtx.forTenant(tenantId),
                user,
                contract,
                ResourceType.THREAT_ARSENAL,
                Action.READ));
  }

  @Nested
  @DisplayName("Read by id")
  class ReadById {

    @Test
    @DisplayName("given_grantInGroupsOfThreeTenantsAndScopeOfTwo_should_refuseWithSingleScopeError")
    void given_grantInGroupsOfThreeTenantsAndScopeOfTwo_should_refuseWithSingleScopeError()
        throws Exception {
      // Arrange: the grant on the built-in contract id sits in a default, an A and a B group
      String contract = ManualContract.MANUAL_DEFAULT;
      grantHolder(Tenant.DEFAULT_TENANT_UUID, contract);
      grantHolder(tenantA, contract);
      grantHolder(tenantB, contract);
      TenantContext.clearCurrentTenant();

      // Act: the scope names A and B; the ambient tenant is the default one
      MvcResult result =
          mvc.perform(
                  get(THREAT_ARSENAL_URL + "/" + contract)
                      .header("X-Tenant-Ids", tenantA + "," + tenantB))
              .andReturn();

      // Assert: the gate lets the request through and the handler refuses it, as a bad request
      assertThat(result.getResponse().getStatus()).isEqualTo(400);
      assertThat(JsonPath.<String>read(result.getResponse().getContentAsString(), "$.message"))
          .isEqualTo(TenantSingleScopeRequiredException.CODE);
    }

    @Test
    @DisplayName("given_grantOnlyInTenantAGroupOnThePrefixedRoute_should_serveTenantACopy")
    void given_grantOnlyInTenantAGroupOnThePrefixedRoute_should_serveTenantACopy()
        throws Exception {
      // Arrange: one contract id copied into A and B, labelled by tenant; the grant is A's only
      String contract = "lookup-" + UUID.randomUUID();
      rights.cloneManualContract(contract, tenantA, "lookup-" + tenantA);
      rights.cloneManualContract(contract, tenantB, "lookup-" + tenantB);
      grantHolder(tenantA, contract);
      TenantContext.clearCurrentTenant();

      // Act
      MvcResult result = mvc.perform(get(tenantUrl(tenantA, contract))).andReturn();

      // Assert
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      assertThat(
              JsonPath.<String>read(
                  result.getResponse().getContentAsString(), "$.action_labels.en"))
          .isEqualTo("lookup-" + tenantA);
    }

    @Test
    @DisplayName("given_grantOnPayloadIdentityOfTenantACopy_should_serveTenantACopy")
    void given_grantOnPayloadIdentityOfTenantACopy_should_serveTenantACopy() throws Exception {
      // Arrange: the copies of A and B each carry a payload of their own; the grant names A's
      String contract = "lookup-" + UUID.randomUUID();
      rights.cloneManualContract(contract, tenantA, "lookup-" + tenantA);
      rights.cloneManualContract(contract, tenantB, "lookup-" + tenantB);
      String identityOfA = rights.withPayloadIdentity(contract, tenantA);
      rights.withPayloadIdentity(contract, tenantB);
      grantHolder(tenantA, identityOfA);
      TenantContext.clearCurrentTenant();

      // Act
      MvcResult onA = mvc.perform(get(tenantUrl(tenantA, contract))).andReturn();

      // Assert
      assertThat(onA.getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName(
        "given_platformGroupGrantOnTenantAIdentityAndScopeOfAOnTheHeaderRoute_should_serveTenantACopy")
    void
        given_platformGroupGrantOnTenantAIdentityAndScopeOfAOnTheHeaderRoute_should_serveTenantACopy()
            throws Exception {
      // Arrange: the default tenant (ambient) and A each copy the contract with a payload of their
      // own; the grant names A's identity and sits in a platform group, which counts in every
      // tenant
      String contract = "lookup-" + UUID.randomUUID();
      rights.cloneManualContract(contract, Tenant.DEFAULT_TENANT_UUID, "lookup-default");
      rights.cloneManualContract(contract, tenantA, "lookup-" + tenantA);
      rights.withPayloadIdentity(contract, Tenant.DEFAULT_TENANT_UUID);
      String identityOfA = rights.withPayloadIdentity(contract, tenantA);
      String platformGroupId = rights.group(null, userId, Set.of());
      rights.grantObserver(platformGroupId, identityOfA);
      testUserHolder.refreshSecurityContext();
      TenantContext.clearCurrentTenant();

      // Act: the scope names A on the non-prefixed route; the ambient tenant is the default one
      MvcResult result =
          mvc.perform(get(THREAT_ARSENAL_URL + "/" + contract).header("X-Tenant-Ids", tenantA))
              .andReturn();

      // Assert: the identity is read from A's copy, so the grant matches and A's copy is served
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      assertThat(
              JsonPath.<String>read(
                  result.getResponse().getContentAsString(), "$.action_labels.en"))
          .isEqualTo("lookup-" + tenantA);
    }
  }

  @Nested
  @DisplayName("Check with a scope of its own")
  class CheckInTenant {

    @Test
    @DisplayName("given_grantOnPayloadIdentityOfTenantACopy_should_holdInTenantAAndNotInTenantB")
    void given_grantOnPayloadIdentityOfTenantACopy_should_holdInTenantAAndNotInTenantB() {
      // Arrange
      String contract = "lookup-" + UUID.randomUUID();
      rights.cloneManualContract(contract, tenantA, "lookup-" + tenantA);
      rights.cloneManualContract(contract, tenantB, "lookup-" + tenantB);
      String identityOfA = rights.withPayloadIdentity(contract, tenantA);
      rights.withPayloadIdentity(contract, tenantB);
      grantHolder(tenantA, identityOfA);
      var user = testUserHolder.get();

      // Act
      boolean inA = holdsRead(tenantA, user, contract);
      boolean inB = holdsRead(tenantB, user, contract);

      // Assert: each tenant takes the payload identity from its own copy
      assertThat(inA).isTrue();
      assertThat(inB).isFalse();
    }

    @Test
    @DisplayName("given_noScopeAndAmbientTenantA_should_takeThePayloadIdentityFromTenantACopy")
    void given_noScopeAndAmbientTenantA_should_takeThePayloadIdentityFromTenantACopy() {
      // Arrange
      String contract = "lookup-" + UUID.randomUUID();
      rights.cloneManualContract(contract, tenantA, "lookup-" + tenantA);
      String identityOfA = rights.withPayloadIdentity(contract, tenantA);
      grantHolder(tenantA, identityOfA);
      var user = testUserHolder.get();

      // Act: the context-less overload the stream listeners use, with tenant A ambient
      TenantContext.setCurrentTenant(tenantA);
      boolean allowed;
      try {
        allowed =
            tenantTx.execute(
                TxCtx.forTenant(tenantA),
                () ->
                    permissionService.hasPermission(
                        user,
                        Optional.empty(),
                        contract,
                        ResourceType.THREAT_ARSENAL,
                        Action.READ));
      } finally {
        TenantContext.clearCurrentTenant();
      }

      // Assert
      assertThat(allowed).isTrue();
    }
  }
}

package io.openaev.rest.injector_contract;

import static io.openaev.rest.injector_contract.InjectorContractApi.TENANT_INJECTOR_CONTRACT_URL;
import static io.openaev.rest.user.MeApi.TENANT_ME_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.injectors.email.EmailContract;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupComposer;
import io.openaev.utils.fixtures.platform.PlatformGroupFixture;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * A threat-arsenal grant applies in the tenant of the group that holds it, or in every tenant when
 * that group is a platform group. Contract ids repeat across tenants, so the rule is what decides
 * which tenant's contract a grant names.
 *
 * <p>The caller is a member of two tenants and holds no capability in either: every access below
 * comes from the grant alone.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("Injector contract grants apply in the tenant of their group")
class InjectorContractGrantTenantTest extends IntegrationTest {

  private static final String CONTRACT_URI = TENANT_INJECTOR_CONTRACT_URL + "/{contractId}";
  private static final String SEARCH_URI = TENANT_INJECTOR_CONTRACT_URL + "/search";
  private static final String CONTRACT = EmailContract.EMAIL_DEFAULT;

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private GrantComposer grantComposer;
  @Autowired private PlatformGroupComposer platformGroupComposer;
  @Autowired private TestUserHolder testUserHolder;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setup() throws Exception {
    tenantGroupComposer.reset();
    platformGroupComposer.reset();
    grantComposer.reset();
    tenantA = tenantHelper.createTenantWithCurrentUser("Grant tenant A");
    tenantB = tenantHelper.createTenantWithCurrentUser("Grant tenant B");
    assertThat(contractRowsIn(tenantA.getId()) + contractRowsIn(tenantB.getId()))
        .as("the contract id exists in both tenants")
        .isEqualTo(2);
  }

  @Nested
  @DisplayName("Given a grant held through a group of tenant A")
  class GrantInTenantGroup {

    @Test
    @DisplayName("the contract is readable by id in tenant A")
    void given_grantInGroupOfTenantA_should_allowContractReadInTenantA() throws Exception {
      // Arrange
      grantThroughTenantGroup(tenantA, Grant.GRANT_TYPE.OBSERVER);

      // Act / Assert
      mvc.perform(get(CONTRACT_URI, tenantA.getId(), CONTRACT)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the contract with the same id is not readable by id in tenant B")
    void given_grantInGroupOfTenantA_should_denyContractReadInTenantB() throws Exception {
      // Arrange
      grantThroughTenantGroup(tenantA, Grant.GRANT_TYPE.OBSERVER);

      // Act / Assert
      mvc.perform(get(CONTRACT_URI, tenantB.getId(), CONTRACT)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("the contract is in tenant A's granted list")
    void given_grantInGroupOfTenantA_should_listContractInTenantA() throws Exception {
      // Arrange
      grantThroughTenantGroup(tenantA, Grant.GRANT_TYPE.OBSERVER);

      // Act / Assert
      mvc.perform(get(TENANT_INJECTOR_CONTRACT_URL, tenantA.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].injector_contract_id", hasItem(CONTRACT)));
    }

    @Test
    @DisplayName("the contract with the same id is not in tenant B's granted list")
    void given_grantInGroupOfTenantA_should_notListContractInTenantB() throws Exception {
      // Arrange
      grantThroughTenantGroup(tenantA, Grant.GRANT_TYPE.OBSERVER);

      // Act / Assert
      mvc.perform(get(TENANT_INJECTOR_CONTRACT_URL, tenantB.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].injector_contract_id", not(hasItem(CONTRACT))));
    }

    @Test
    @DisplayName("the contract is found by the search in tenant A")
    void given_grantInGroupOfTenantA_should_findContractBySearchInTenantA() throws Exception {
      // Arrange
      grantThroughTenantGroup(tenantA, Grant.GRANT_TYPE.OBSERVER);

      // Act / Assert
      search(tenantA).andExpect(jsonPath("$.content[*].injector_contract_id", hasItem(CONTRACT)));
    }

    @Test
    @DisplayName("the contract with the same id is not found by the search in tenant B")
    void given_grantInGroupOfTenantA_should_notFindContractBySearchInTenantB() throws Exception {
      // Arrange
      grantThroughTenantGroup(tenantA, Grant.GRANT_TYPE.OBSERVER);

      // Act / Assert
      search(tenantB)
          .andExpect(jsonPath("$.content[*].injector_contract_id", not(hasItem(CONTRACT))));
    }

    @Test
    @DisplayName("a planner grant passes the delete access check in tenant A")
    void given_plannerGrantInGroupOfTenantA_should_passDeleteAccessCheckInTenantA()
        throws Exception {
      // Arrange
      grantThroughTenantGroup(tenantA, Grant.GRANT_TYPE.PLANNER);

      // Act / Assert: past the access check, the service refuses to delete a built-in contract.
      mvc.perform(delete(CONTRACT_URI, tenantA.getId(), CONTRACT).with(csrf()))
          .andExpect(status().isBadRequest());
      assertThat(contractRowsIn(tenantA.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("a planner grant does not pass the delete access check in tenant B")
    void given_plannerGrantInGroupOfTenantA_should_failDeleteAccessCheckInTenantB()
        throws Exception {
      // Arrange
      grantThroughTenantGroup(tenantA, Grant.GRANT_TYPE.PLANNER);

      // Act / Assert
      mvc.perform(delete(CONTRACT_URI, tenantB.getId(), CONTRACT).with(csrf()))
          .andExpect(status().isForbidden());
      assertThat(contractRowsIn(tenantB.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("tenant A's user grants and the backend check agree on the contract")
    void given_grantInGroupOfTenantA_should_agreeWithUserGrantsInTenantA() throws Exception {
      // Arrange
      grantThroughTenantGroup(tenantA, Grant.GRANT_TYPE.OBSERVER);

      // Act / Assert
      mvc.perform(get(TENANT_ME_URI, tenantA.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user_grants['" + CONTRACT + "']").exists());
      mvc.perform(get(CONTRACT_URI, tenantA.getId(), CONTRACT)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("tenant B's user grants and the backend check agree on the contract")
    void given_grantInGroupOfTenantA_should_agreeWithUserGrantsInTenantB() throws Exception {
      // Arrange
      grantThroughTenantGroup(tenantA, Grant.GRANT_TYPE.OBSERVER);

      // Act / Assert
      mvc.perform(get(TENANT_ME_URI, tenantB.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.user_grants['" + CONTRACT + "']").doesNotExist());
      mvc.perform(get(CONTRACT_URI, tenantB.getId(), CONTRACT)).andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Given a grant held through a platform group")
  class GrantInPlatformGroup {

    @Test
    @DisplayName("the contract is readable by id in tenant A")
    void given_grantInPlatformGroup_should_allowContractReadInTenantA() throws Exception {
      // Arrange
      grantThroughPlatformGroup();

      // Act / Assert
      mvc.perform(get(CONTRACT_URI, tenantA.getId(), CONTRACT)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the contract is readable by id in tenant B")
    void given_grantInPlatformGroup_should_allowContractReadInTenantB() throws Exception {
      // Arrange
      grantThroughPlatformGroup();

      // Act / Assert
      mvc.perform(get(CONTRACT_URI, tenantB.getId(), CONTRACT)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("the contract is in tenant B's granted list")
    void given_grantInPlatformGroup_should_listContractInTenantB() throws Exception {
      // Arrange
      grantThroughPlatformGroup();

      // Act / Assert
      mvc.perform(get(TENANT_INJECTOR_CONTRACT_URL, tenantB.getId()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].injector_contract_id", hasItem(CONTRACT)));
    }

    @Test
    @DisplayName("the contract is found by the search in tenant B")
    void given_grantInPlatformGroup_should_findContractBySearchInTenantB() throws Exception {
      // Arrange
      grantThroughPlatformGroup();

      // Act / Assert
      search(tenantB).andExpect(jsonPath("$.content[*].injector_contract_id", hasItem(CONTRACT)));
    }
  }

  // -- Helpers --

  private void grantThroughTenantGroup(Tenant tenant, Grant.GRANT_TYPE grantType) {
    String previous = TenantContext.getCurrentTenant();
    TenantContext.setCurrentTenant(tenant.getId());
    try {
      Group group = TenantGroupFixture.getGroup("Grant holders " + tenant.getName());
      group.setUsers(new ArrayList<>(List.of(testUserHolder.get())));
      tenantGroupComposer
          .forGroup(group)
          .withGrant(
              grantComposer.forGrant(GrantFixture.getGrantForThreatArsenal(CONTRACT, grantType)))
          .persist();
    } finally {
      TenantContext.setCurrentTenant(previous);
    }
    reloadCurrentUser();
  }

  private void grantThroughPlatformGroup() {
    Group group = PlatformGroupFixture.getPlatformGroup("Grant holders platform");
    group.setUsers(new ArrayList<>(List.of(testUserHolder.get())));
    platformGroupComposer.forPlatformGroup(group).persist();
    Grant grant = GrantFixture.getGrantForThreatArsenal(CONTRACT, Grant.GRANT_TYPE.OBSERVER);
    grant.setGroup(group);
    grantComposer.forGrant(grant).persist();
    reloadCurrentUser();
  }

  private void reloadCurrentUser() {
    entityManager.flush();
    entityManager.clear();
    testUserHolder.refreshSecurityContext();
  }

  private ResultActions search(Tenant tenant) throws Exception {
    return mvc.perform(
            post(SEARCH_URI, tenant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(PaginationFixture.getDefault().size(100).build()))
                .with(csrf()))
        .andExpect(status().isOk());
  }

  /** Raw JDBC on the test's connection: ground truth outside any entity filter. */
  private long contractRowsIn(String tenantId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM injectors_contracts"
                          + " WHERE injector_contract_id = ? AND tenant_id = ?")) {
                statement.setString(1, CONTRACT);
                statement.setString(2, tenantId);
                try (var result = statement.executeQuery()) {
                  result.next();
                  return result.getLong(1);
                }
              }
            });
  }
}

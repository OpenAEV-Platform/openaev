package io.openaev.rest.injector_contract;

import static io.openaev.rest.injector_contract.InjectorContractApi.TENANT_INJECTOR_CONTRACT_URL;
import static io.openaev.rest.payload.PayloadApi.TENANT_PAYLOAD_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.GrantFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.composers.GrantComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("A threat arsenal grant on a payload identity")
class PayloadIdentityGrantTest extends IntegrationTest {

  private static final String CONTRACT_URI = TENANT_INJECTOR_CONTRACT_URL + "/{contractId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private GrantComposer grantComposer;
  @Autowired private TestUserHolder testUserHolder;

  private Tenant tenant;
  private final String identityId = UUID.randomUUID().toString();
  private final String contractId = UUID.randomUUID().toString();

  @BeforeEach
  void setup() throws Exception {
    tenantGroupComposer.reset();
    grantComposer.reset();
    tenant = tenantHelper.createTenantWithCurrentUser("Payload identity grant");
    execute(
        "INSERT INTO payload_identities (payload_identity_id, tenant_id, payload_source)"
            + " VALUES (?1, ?2, 'MANUAL')",
        identityId,
        tenant.getId());
    execute(
        "INSERT INTO payloads (payload_id, payload_type, payload_name, tenant_id,"
            + " payload_identity, payload_version) VALUES (?1, 'Command', 'p', ?2, ?1, '1.0.0')",
        identityId,
        tenant.getId());
    execute(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " injector_contract_payload, tenant_id) VALUES (?1, '{}', ?2, ?3)",
        contractId,
        identityId,
        tenant.getId());
    grantOnIdentity();
  }

  @Test
  @DisplayName("the contract is readable by id")
  void given_grantOnPayloadIdentity_should_allowContractRead() throws Exception {
    // Act / Assert
    mvc.perform(get(CONTRACT_URI, tenant.getId(), contractId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("the contract is in the granted list")
  void given_grantOnPayloadIdentity_should_listContract() throws Exception {
    // Act / Assert
    mvc.perform(get(TENANT_INJECTOR_CONTRACT_URL, tenant.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].injector_contract_id", hasItem(contractId)));
  }

  @Test
  @DisplayName("the contract is found by the search")
  void given_grantOnPayloadIdentity_should_findContractBySearch() throws Exception {
    // Act / Assert
    mvc.perform(
            post(TENANT_INJECTOR_CONTRACT_URL + "/search", tenant.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(PaginationFixture.getDefault().size(100).build()))
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[*].injector_contract_id", hasItem(contractId)));
  }

  @Test
  @WithMockUser(isAdmin = true)
  @DisplayName("deleting the last version of a payload deletes its identity and the grants on it")
  void given_grantOnPayloadIdentity_should_beDeletedWithThePayload() throws Exception {
    // Act
    mvc.perform(
            delete(TENANT_PAYLOAD_URI + "/{payloadId}", tenant.getId(), identityId).with(csrf()))
        .andExpect(status().is2xxSuccessful());

    // Assert
    assertThat(
            entityManager
                .createNativeQuery("SELECT grant_id FROM grants WHERE grant_resource = :id")
                .setParameter("id", identityId)
                .getResultList())
        .isEmpty();
    assertThat(
            entityManager
                .createNativeQuery(
                    "SELECT payload_identity_id FROM payload_identities"
                        + " WHERE payload_identity_id = :id")
                .setParameter("id", identityId)
                .getResultList())
        .isEmpty();
  }

  private void grantOnIdentity() {
    String previous = TenantContext.getCurrentTenant();
    TenantContext.setCurrentTenant(tenant.getId());
    try {
      Group group = TenantGroupFixture.getGroup("Identity grant holders");
      group.setUsers(new ArrayList<>(List.of(testUserHolder.get())));
      tenantGroupComposer
          .forGroup(group)
          .withGrant(
              grantComposer.forGrant(
                  GrantFixture.getGrantForThreatArsenal(identityId, Grant.GRANT_TYPE.OBSERVER)))
          .persist();
    } finally {
      TenantContext.setCurrentTenant(previous);
    }
    entityManager.flush();
    entityManager.clear();
    testUserHolder.refreshSecurityContext();
  }

  private void execute(String sql, Object... params) {
    var query = entityManager.createNativeQuery(sql);
    for (int i = 0; i < params.length; i++) {
      query.setParameter(i + 1, params[i]);
    }
    query.executeUpdate();
  }
}

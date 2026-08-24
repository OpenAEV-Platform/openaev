package io.openaev.rest.atomic_testing;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.rest.atomic_testing.form.AtomicTestingInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * With {@code injectors} activated, creating an atomic testing with an explicit injector id must
 * only resolve an injector the caller's tenant can see. Regression test for a gap found manually
 * testing the create-atomic-testing flow: {@code AtomicTestingApi#createAtomicTesting} had no
 * {@code TxCtx}, so {@code InjectUtils#resolveInjector} read the v2-scoped {@code injectors} table
 * with no tenant scope and 404'd even for the caller's own injector.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=injectors")
@WithMockUser(isAdmin = true)
@DisplayName("AtomicTestingApi scopes the explicit injector lookup to the caller's tenants")
class AtomicTestingApiTenantIsolationTest extends IntegrationTest {

  private static final String CREATE = "/api/tenants/{tenantId}/atomic-testings";
  private static final String DETAIL = "/api/tenants/{tenantId}/atomic-testings/{injectId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ObjectMapper objectMapper;

  private String tenantA;
  private String injectorA;
  private String injectorB;

  @BeforeEach
  void seedTwoTenantsWithOneInjectorEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("at-iso-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("at-iso-b").getId();
    injectorA = seedInjector(tenantA, "at-injector-a");
    injectorB = seedInjector(tenantB, "at-injector-b");
  }

  @Test
  @DisplayName(
      "under tenant A's path: A's own injector is resolved and the atomic testing is created")
  void underTenantAWithOwnInjectorIsCreated() throws Exception {
    mvc.perform(request(tenantA, injectorA)).andExpect(status().is2xxSuccessful());
  }

  @Test
  @DisplayName("under tenant A's path: B's injector is not found (cross-tenant lookup blocked)")
  void underTenantAWithCrossTenantInjectorIsNotFound() throws Exception {
    mvc.perform(request(tenantA, injectorB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "GET atomic-testing detail resolves inject_type through the v2-scoped injectors table")
  void detailExposesInjectTypeUnderV2Activation() throws Exception {
    // Regression for a gap found in the side-by-side of the agent-implant E2E: the detail read
    // AtomicTestingApi#findAtomicTesting had no TxCtx, so Inject#getType() resolved the inject's
    // injector on the v2-scoped injectors table with no tenant scope and returned null. The
    // frontend gates the "Results by target" panel (and its execution-trace fetch) on a non-null
    // inject_type, so a null value silently broke the atomic-testing result view.
    String body =
        mvc.perform(request(tenantA, injectorA))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String injectId = objectMapper.readTree(body).get("inject_id").asText();

    mvc.perform(get(DETAIL, tenantA, injectId).with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.inject_type").value("openaev_email"));
  }

  private MockHttpServletRequestBuilder request(String tenantId, String injectorId)
      throws Exception {
    AtomicTestingInput input = InjectFixture.createAtomicTesting("iso-test", null);
    input.setInjectorId(injectorId);
    return post(CREATE, tenantId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(asJsonString(input))
        .with(csrf());
  }

  private String seedInjector(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO injectors (injector_id, tenant_id, injector_name, injector_type,"
                + " injector_external, injector_custom_contracts, injector_payloads,"
                + " injector_created_at, injector_updated_at)"
                + " VALUES (:id, :tenant, :name, :type, false, false, false, now(), now())")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("name", name)
        .setParameter("type", "openaev_email")
        .executeUpdate();
    return id;
  }
}

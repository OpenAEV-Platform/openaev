package io.openaev.rest.asset.endpoint;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Endpoint;
import io.openaev.rest.asset.endpoint.form.EndpointInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Endpoint creation must carry the tenant it belongs to explicitly, and must refuse an ambiguous
 * write rather than attribute it silently.
 *
 * <p>Today {@code EndpointApi#createEndpoint} passes {@code TenantContext.getCurrentTenant()}, the
 * v1 thread-local, which always yields something: the DEFAULT tenant when unset. So an ambiguous
 * create succeeds and the row lands somewhere. {@code /register}, three methods below in the same
 * controller, already resolves through {@link io.openaev.config.TenantWriteScopeResolver}; these
 * tests hold the create paths to that same standard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@WithMockUser(isAdmin = true)
@DisplayName("endpoint creation carries its tenant explicitly")
class EndpointWriteAttributionTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;

  private String tenantA;

  @BeforeEach
  void createTwoTenantsTheUserBelongsTo() throws Exception {
    // TWO tenants, deliberately: with a single membership a create without a selector is not
    // ambiguous at all and TenantWriteScopeResolver resolves it legitimately. The refusal this
    // class asserts only exists when the caller spans several tenants, which is the real-world
    // shape the v1 thread-local used to paper over by falling back to DEFAULT.
    tenantA = tenantHelper.createTenantWithCurrentUser("ep-write-a-" + UUID.randomUUID()).getId();
    tenantHelper.createTenantWithCurrentUser("ep-write-b-" + UUID.randomUUID());
  }

  @Test
  @DisplayName("a create under tenant A's path is stored with tenant A's id")
  void createUnderTenantPathIsAttributedToThatTenant() throws Exception {
    String body =
        mvc.perform(
                post("/api/tenants/{tenantId}/endpoints/agentless", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(endpointInput("attributed-endpoint")))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(body, "$.asset_id");
    assertEquals(
        tenantA,
        rawTenantOf(createdId),
        "the endpoint must be stored under the tenant its request selected");
  }

  @Test
  @DisplayName(
      "a create with no selector from a two-tenant caller is refused, not silently attributed")
  void createWithoutSelectorIsRejected() throws Exception {
    mvc.perform(
            post("/api/endpoints/agentless")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(endpointInput("no-selector-endpoint")))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  private EndpointInput endpointInput(String name) {
    EndpointInput input = new EndpointInput();
    input.setName(name + "-" + UUID.randomUUID());
    input.setPlatform(Endpoint.PLATFORM_TYPE.Linux);
    input.setArch(Endpoint.PLATFORM_ARCH.x86_64);
    input.setIps(new String[] {"10.0.0.1"});
    return input;
  }

  /** Ground truth by raw JDBC: createNativeQuery would be scoped by the inspector like any read. */
  private String rawTenantOf(String assetId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement st =
                  connection.prepareStatement("SELECT tenant_id FROM assets WHERE asset_id = ?")) {
                st.setString(1, assetId);
                try (ResultSet rs = st.executeQuery()) {
                  return rs.next() ? rs.getString(1) : null;
                }
              }
            });
  }
}

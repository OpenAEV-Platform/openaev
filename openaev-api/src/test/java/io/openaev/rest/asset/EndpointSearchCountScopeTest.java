package io.openaev.rest.asset;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.service.EndpointService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A paginated search issues two queries, the data page and a separate count, and both must be
 * filtered. A filtered page with an unfiltered count is worse than an outright leak: the list looks
 * correctly empty while the number beside it says another tenant has rows.
 *
 * <p>Each half is asserted separately, and a third case runs the same SQL with no scope to prove
 * the inspector is actually firing. Without that third case the two green assertions would also
 * pass in a context where the table is not active, which is exactly how EndpointApiTest's
 * cross-tenant tests stayed green while isolating nothing (#6438).
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@WithMockUser
@DisplayName("a paginated endpoint search filters its count, not just its page")
class EndpointSearchCountScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private EndpointService endpointService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private EntityManager entityManager;

  private String tenantA;
  private String tenantB;
  private static final String NAME = "count-scope-endpoint";

  @BeforeEach
  void seedOneEndpointInTenantA() throws Exception {
    // Tenants built through the capability chain rather than plain membership, so the caller is a
    // realistic non-admin holding real grants in both.
    tenantA =
        tenantHelper
            .createTenantWithCapabilities(
                "count-a-" + UUID.randomUUID(),
                java.util.Set.of(Capability.MANAGE_ASSETS, Capability.ACCESS_ASSETS))
            .getId();
    tenantB =
        tenantHelper
            .createTenantWithCapabilities(
                "count-b-" + UUID.randomUUID(), java.util.Set.of(Capability.ACCESS_ASSETS))
            .getId();
    // Created through the HTTP tenant path rather than seeded in SQL, so the same route production
    // uses is what attributes the row.
    io.openaev.database.model.Endpoint input =
        io.openaev.utils.fixtures.EndpointFixture.createEndpoint();
    input.setName(NAME);
    input.setHostname(NAME);
    mvc.perform(
            post("/api/tenants/{tenantId}/endpoints/agentless", tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
    entityManager.flush();
    entityManager.clear();
  }

  @Test
  @DisplayName("under tenant A: the endpoint is found, and counted once")
  void ownTenantSeesItInBothHalves() {
    Page<Endpoint> page = searchUnder(tenantA);
    assertEquals(1, page.getContent().size(), "A's endpoint must be on the page");
    assertEquals(1, page.getTotalElements(), "and counted once");
  }

  @Test
  @DisplayName("under tenant B: neither the page nor the count sees A's endpoint")
  void otherTenantSeesItInNeitherHalf() {
    Page<Endpoint> page = searchUnder(tenantB);
    assertEquals(0, page.getContent().size(), "A's endpoint must not be on B's page");
    assertEquals(
        0,
        page.getTotalElements(),
        "and must not be counted either: a filtered page with an unfiltered count shows an empty"
            + " list next to a non-zero total, which is how a cross-tenant leak hides");
  }

  @Test
  @DisplayName("through HTTP under tenant B: totalElements must be zero too")
  void httpSearchUnderOtherTenantCountsZero() throws Exception {
    // Commit the seed and search in a fresh transaction: a scope carried over from the create would
    // make the assertion pass for the wrong reason.
    entityManager.flush();
    entityManager.clear();
    org.springframework.test.context.transaction.TestTransaction.flagForCommit();
    org.springframework.test.context.transaction.TestTransaction.end();
    org.springframework.test.context.transaction.TestTransaction.start();
    // The combination neither existing test covered: the HTTP route AND the count.
    // AssetHttpIsolationTest
    // goes through HTTP but asserts on the body, and the two cases above assert the count but set
    // the
    // scope directly. EndpointApiTest fails exactly here.
    String body =
        mvc.perform(
                // Concatenated URI, not a template: the tenant selector must come from the
                // resolved path variable, not from the builder's URI template.
                post("/api/tenants/" + tenantB + "/endpoints/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(PaginationFixture.simpleTextSearch(NAME)))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(
        0,
        ((Number) JsonPath.read(body, "$.totalElements")).intValue(),
        "an endpoint owned by another tenant must not be counted: " + body);
  }

  private Page<Endpoint> searchUnder(String tenantId) {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantId));
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    return endpointService.searchEndpoints(PaginationFixture.simpleTextSearch(NAME));
  }
}

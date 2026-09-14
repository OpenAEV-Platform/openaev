package io.openaev.rest.domain;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.rest.domain.form.DomainBaseInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
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

@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=domains")
@WithMockUser(isAdmin = true)
@DisplayName("domains read and write isolation through the real HTTP endpoint")
class DomainHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_DOMAINS = "/api/tenants/{tenantId}/domains";
  private static final String TENANT_DOMAIN_BY_ID = TENANT_DOMAINS + "/{domainId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String domainA;
  private String domainB;

  @BeforeEach
  void seedTwoTenantsWithOneDomainEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("domain-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("domain-iso-b").getId();
    domainA = seedDomain(tenantA, "domain-a");
    domainB = seedDomain(tenantB, "domain-b");
  }

  @Test
  @DisplayName("under tenant A's path: A's domain is visible, B's is hidden")
  void underTenantAPath() throws Exception {
    mvc.perform(get(TENANT_DOMAIN_BY_ID, tenantA, domainA)).andExpect(status().isOk());
    mvc.perform(get(TENANT_DOMAIN_BY_ID, tenantA, domainB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("under tenant A's path: list returns A's domain and not B's")
  void listUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(TENANT_DOMAINS, tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(domainA), "A's domain must appear in A's list");
    assertFalse(response.contains(domainB), "B's domain must not appear in A's list");
  }

  @Test
  @DisplayName("via the X-Tenant-Ids header (no path tenant): list returns A's domain only")
  void listViaHeaderReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get("/api/domains").header("X-Tenant-Ids", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(domainA), "A's domain must appear when A is selected");
    assertFalse(response.contains(domainB), "B's domain must not appear");
  }

  @Test
  @DisplayName("an upsert under tenant A's path is attributed to tenant A")
  void upsertUnderTenantAIsAttributedToA() throws Exception {
    DomainBaseInput input = new DomainBaseInput();
    input.setName("created-under-a");
    input.setColor("#112233");

    String response =
        mvc.perform(
                post(TENANT_DOMAINS + "/{domainId}/upsert", tenantA, "ignored")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(response, "$.domain_id");
    String storedTenant =
        (String)
            entityManager
                .createNativeQuery("SELECT tenant_id FROM domains WHERE domain_id = ?1")
                .setParameter(1, createdId)
                .getSingleResult();
    assertEquals(tenantA, storedTenant, "the upserted domain must belong to tenant A");
  }

  @Test
  @DisplayName("an upsert with no tenant selector is refused")
  void upsertWithoutSelectorIsRejected() throws Exception {
    DomainBaseInput input = new DomainBaseInput();
    input.setName("no-selector");
    input.setColor("#223344");

    mvc.perform(
            post("/api/domains/{domainId}/upsert", "ignored")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("upserting the same name under A and B yields two distinct rows")
  void upsertSameNameUnderTwoTenantsYieldsTwoRows() throws Exception {
    seedDomain(tenantB, "shared-name");

    DomainBaseInput input = new DomainBaseInput();
    input.setName("shared-name");
    input.setColor("#334455");
    mvc.perform(
            post(TENANT_DOMAINS + "/{domainId}/upsert", tenantA, "ignored")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk());

    entityManager.flush();
    long rowCount =
        entityManager
            .unwrap(Session.class)
            .doReturningWork(
                connection -> {
                  try (PreparedStatement stmt =
                      connection.prepareStatement(
                          "SELECT count(*) FROM domains WHERE domain_name = ?")) {
                    stmt.setString(1, "shared-name");
                    try (ResultSet rs = stmt.executeQuery()) {
                      rs.next();
                      return rs.getLong(1);
                    }
                  }
                });
    assertEquals(2L, rowCount, "each tenant must own an independent row for the same name");
  }

  private String seedDomain(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO domains (domain_id, domain_name, domain_color, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, "#445566")
        .setParameter(4, tenantId)
        .executeUpdate();
    return id;
  }
}

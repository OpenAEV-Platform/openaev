package io.openaev.rest.tag_rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
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
@TestPropertySource(properties = "openaev.tenant.active-tables=tag_rules")
@WithMockUser(isAdmin = true)
@DisplayName("tag_rules read and write isolation through TagRuleApi")
class TagRuleHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_TAG_RULES = "/api/tenants/{tenantId}/tag-rules";
  private static final String TENANT_TAG_RULE_BY_ID = TENANT_TAG_RULES + "/{tagRuleId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String tagRuleA;
  private String tagRuleB;
  private String tagA;
  private String tagB;

  @BeforeEach
  void seedTwoTenantsWithOneTagRuleEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("tag-rule-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("tag-rule-iso-b").getId();
    tagA = seedTag(tenantA, "tag-a");
    tagB = seedTag(tenantB, "tag-b");
    tagRuleA = seedTagRule(tenantA, tagA);
    tagRuleB = seedTagRule(tenantB, tagB);
  }

  @Test
  @DisplayName("under tenant A path, A's tag rule is visible and B's is not found")
  void readIsolationByPath() throws Exception {
    mvc.perform(get(TENANT_TAG_RULE_BY_ID, tenantA, tagRuleA)).andExpect(status().isOk());
    mvc.perform(get(TENANT_TAG_RULE_BY_ID, tenantA, tagRuleB)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("header route only returns selected tenant rows")
  void headerRouteIsolation() throws Exception {
    String response =
        mvc.perform(get("/api/tag-rules").header("X-Tenant-Ids", tenantA).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(response, "$[*].tag_rule_id");
    assertTrue(ids.contains(tagRuleA));
    assertFalse(ids.contains(tagRuleB));
  }

  @Test
  @DisplayName("create under tenant path is attributed to tenant")
  void createIsAttributed() throws Exception {
    String createTagName = "tag-create-a";
    seedTag(tenantA, createTagName);
    String body = "{\"tag_name\":\"" + createTagName + "\",\"asset_groups\":[]}";
    String response =
        mvc.perform(
                post(TENANT_TAG_RULES, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(response, "$.tag_rule_id");
    assertEquals(tenantA, rawTenant(createdId));
  }

  @Test
  @DisplayName("create without selector is refused")
  void createWithoutSelectorIsRejected() throws Exception {
    seedTag(tenantA, "tag-no-selector");
    mvc.perform(
            post("/api/tag-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tag_name\":\"tag-no-selector\",\"asset_groups\":[]}")
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("cross-tenant update is not found and leaves row untouched")
  void crossTenantUpdateIsBlocked() throws Exception {
    seedTag(tenantA, "tag-update-a");
    String body = "{\"tag_name\":\"tag-update-a\",\"asset_groups\":[]}";
    mvc.perform(
            put(TENANT_TAG_RULE_BY_ID, tenantA, tagRuleB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(tenantB, rawTenant(tagRuleB));
  }

  @Test
  @DisplayName("cross-tenant delete is not found and leaves row untouched")
  void crossTenantDeleteIsBlocked() throws Exception {
    // TagRuleService.deleteTagRule looks the row up via findById (scoped by the
    // inspector) and throws ElementNotFoundException when it is out of scope,
    // matching TagRuleApiTest#deleteTagRule_WITH_unexisting_id's 404 semantics.
    mvc.perform(delete(TENANT_TAG_RULE_BY_ID, tenantA, tagRuleB).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount(tagRuleB));
  }

  private String seedTag(String tenantId, String namePrefix) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO tags (tag_id, tag_name, tag_color, tag_created_at, tag_updated_at, tenant_id)"
                + " VALUES (?1, ?2, ?3, now(), now(), ?4)")
        .setParameter(1, id)
        // Use namePrefix verbatim (not a random suffix): callers pass the exact
        // name they later reference in a request body (e.g. createIsAttributed).
        // The (tag_name, tenant_id) unique constraint keeps this collision-free
        // per tenant, and each test method rolls back independently.
        .setParameter(2, namePrefix)
        .setParameter(3, "#000000")
        .setParameter(4, tenantId)
        .executeUpdate();
    return id;
  }

  private String seedTagRule(String tenantId, String tagId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO tag_rules (tag_rule_id, tag_id, tag_rule_protected, tenant_id)"
                + " VALUES (?1, ?2, false, ?3)")
        .setParameter(1, id)
        .setParameter(2, tagId)
        .setParameter(3, tenantId)
        .executeUpdate();
    return id;
  }

  private String rawTenant(String tagRuleId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var statement =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM tag_rules WHERE tag_rule_id = ?")) {
                statement.setString(1, tagRuleId);
                try (var rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private long rawCount(String tagRuleId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM tag_rules WHERE tag_rule_id = ?")) {
                statement.setString(1, tagRuleId);
                try (var rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}

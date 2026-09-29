package io.openaev.rest.tag_rule;

import static io.openaev.utils.JsonTestUtils.asJsonString;
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
import io.openaev.rest.tag_rule.form.TagRuleInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
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

/**
 * tag_rules read/write isolation through the real HTTP endpoint (v2 activation, #6407). Follows the
 * same shape as {@code TagHttpIsolationTest} (tags, already v2-active): every row is seeded with a
 * raw native INSERT carrying an explicit tenant_id, never through v1 TenantContext.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=tags,tag_rules")
@WithMockUser(isAdmin = true)
@DisplayName("tag_rules read and write isolation through the real HTTP endpoint")
class TagRuleHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_TAG_RULES = "/api/tenants/{tenantId}/tag-rules";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String tagRuleA;
  private String tagRuleB;
  private String tagNameA;

  @BeforeEach
  void seedTwoTenantsWithOneTagRuleEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("tag-rule-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("tag-rule-iso-b").getId();
    tagNameA = "tag-rule-iso-a-" + System.currentTimeMillis();
    String tagIdA = seedTag(tenantA, tagNameA, "#111111");
    String tagIdB = seedTag(tenantB, "tag-rule-iso-b-" + System.currentTimeMillis(), "#222222");
    tagRuleA = seedTagRule(tenantA, tagIdA);
    tagRuleB = seedTagRule(tenantB, tagIdB);
  }

  @Test
  @DisplayName("under tenant A's path: read returns A's tag rule")
  void readOwnRowUnderOwnPath() throws Exception {
    String response =
        mvc.perform(
                get(TENANT_TAG_RULES + "/{id}", tenantA, tagRuleA)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals(tagRuleA, JsonPath.read(response, "$.tag_rule_id"));
  }

  @Test
  @DisplayName("under tenant B's path: reading tenant A's tag rule returns nothing")
  void readCrossTenantReturnsNothing() throws Exception {
    String response =
        mvc.perform(
                get(TENANT_TAG_RULES + "/{id}", tenantB, tagRuleA)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertEquals("", response, "a tag rule from another tenant must not be readable");
  }

  @Test
  @DisplayName("under tenant A's path: list returns A's rule and not B's")
  void listUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(TENANT_TAG_RULES, tenantA).accept(MediaType.APPLICATION_JSON).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(response.contains(tagRuleA), "A's tag rule must appear under tenant A's path");
    assertFalse(response.contains(tagRuleB), "B's tag rule must not appear under tenant A's path");
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's rule and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post(TENANT_TAG_RULES + "/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(response.contains(tagRuleA), "A's tag rule must appear in A's search results");
    assertFalse(response.contains(tagRuleB), "B's tag rule must not appear in A's search results");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    TagRuleInput input = TagRuleInput.builder().tagName(tagNameA).assetGroups(List.of()).build();

    String response =
        mvc.perform(
                post(TENANT_TAG_RULES, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(response, "$.tag_rule_id");
    String storedTenant = rawTenantId(createdId);
    assertEquals(tenantA, storedTenant, "the created tag rule must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    TagRuleInput input = TagRuleInput.builder().tagName(tagNameA).assetGroups(List.of()).build();

    mvc.perform(
            post("/api/tag-rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("an update from tenant B's path on tenant A's tag rule is rejected")
  void updateCrossTenantIsRejected() throws Exception {
    TagRuleInput input = TagRuleInput.builder().tagName(tagNameA).assetGroups(List.of()).build();

    mvc.perform(
            put(TENANT_TAG_RULES + "/{id}", tenantB, tagRuleA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("a delete from tenant B's path on tenant A's tag rule is rejected")
  void deleteCrossTenantIsRejected() throws Exception {
    mvc.perform(delete(TENANT_TAG_RULES + "/{id}", tenantB, tagRuleA).with(csrf()))
        .andExpect(status().isNotFound());

    // ground truth: the row must still exist
    entityManager.flush();
    entityManager.clear();
    Long count =
        entityManager
            .unwrap(Session.class)
            .doReturningWork(
                connection -> {
                  try (var stmt =
                      connection.prepareStatement(
                          "SELECT count(*) FROM tag_rules WHERE tag_rule_id = ?")) {
                    stmt.setString(1, tagRuleA);
                    try (var rows = stmt.executeQuery()) {
                      rows.next();
                      return rows.getLong(1);
                    }
                  }
                });
    assertEquals(1L, count, "the tag rule must not have been deleted cross-tenant");
  }

  private String rawTenantId(String tagRuleId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM tag_rules WHERE tag_rule_id = ?")) {
                stmt.setString(1, tagRuleId);
                try (var rows = stmt.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private String seedTag(String tenantId, String name, String color) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO tags (tag_id, tag_name, tag_color, tenant_id) VALUES (?1, ?2, ?3, ?4)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, color)
        .setParameter(4, tenantId)
        .executeUpdate();
    return id;
  }

  private String seedTagRule(String tenantId, String tagId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO tag_rules (tag_rule_id, tag_id, tenant_id) VALUES (?1, ?2, ?3)")
        .setParameter(1, id)
        .setParameter(2, tagId)
        .setParameter(3, tenantId)
        .executeUpdate();
    return id;
  }
}

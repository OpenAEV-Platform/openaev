package io.openaev.rest.tag_rule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=tags,tag_rules")
@WithMockUser(isAdmin = false)
@DisplayName("tag_rules isolation holds for a non-admin spanning two tenants")
class TagRuleNonAdminIsolationTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String tagRuleA;
  private String tagRuleB;

  @BeforeEach
  void seedTwoTenantsTheNonAdminBelongsToWithOneTagRuleEach() throws Exception {
    tenantA =
        tenantHelper
            .createTenantWithCapabilities(
                "nonadmin-tag-rule-a", Set.of(Capability.ACCESS_TENANT_SETTINGS))
            .getId();
    tenantB =
        tenantHelper
            .createTenantWithCapabilities(
                "nonadmin-tag-rule-b", Set.of(Capability.ACCESS_TENANT_SETTINGS))
            .getId();
    String tagIdA =
        seedTag(tenantA, "nonadmin-tag-rule-a-" + System.currentTimeMillis(), "#111111");
    String tagIdB =
        seedTag(tenantB, "nonadmin-tag-rule-b-" + System.currentTimeMillis(), "#222222");
    tagRuleA = seedTagRule(tenantA, tagIdA);
    tagRuleB = seedTagRule(tenantB, tagIdB);
  }

  @Test
  @DisplayName("a non-admin listing under tenant A's path sees only A's tag rule")
  void listUnderTenantAReturnsOnlyAForNonAdmin() throws Exception {
    String response =
        mvc.perform(get("/api/tenants/{tenantId}/tag-rules", tenantA).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(
        response.contains(tagRuleA), "A's tag rule must appear for the non-admin member of A");
    assertFalse(response.contains(tagRuleB), "B's tag rule must not leak to A's scope");
  }

  @Test
  @DisplayName("a non-admin listing under tenant B's path sees only B's tag rule")
  void listUnderTenantBReturnsOnlyBForNonAdmin() throws Exception {
    String response =
        mvc.perform(get("/api/tenants/{tenantId}/tag-rules", tenantB).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(
        response.contains(tagRuleB), "B's tag rule must appear for the non-admin member of B");
    assertFalse(response.contains(tagRuleA), "A's tag rule must not leak to B's scope");
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

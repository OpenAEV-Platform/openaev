package io.openaev.rest.tag_rule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
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
@TestPropertySource(properties = "openaev.tenant.active-tables=tag_rules")
@WithMockUser(isAdmin = false)
@DisplayName("tag_rules isolation holds for non-admin users")
class TagRuleNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> READ_TAG_RULES = Set.of(Capability.ACCESS_TENANT_SETTINGS);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tagRuleA;
  private String tagRuleB;

  @BeforeEach
  void seed() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("tag-rule-na-a", READ_TAG_RULES).getId();
    String tenantB =
        tenantHelper.createTenantWithCapabilities("tag-rule-na-b", READ_TAG_RULES).getId();
    String tagA = seedTag(tenantA, "tag-na-a");
    String tagB = seedTag(tenantB, "tag-na-b");
    tagRuleA = seedTagRule(tenantA, tagA);
    tagRuleB = seedTagRule(tenantB, tagB);
  }

  @Test
  @DisplayName("non-admin list under tenant A only returns A")
  void nonAdminTenantPathIsIsolated() throws Exception {
    String response =
        mvc.perform(get("/api/tenants/{tenantId}/tag-rules", tenantA).with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(response, "$[*].tag_rule_id");
    assertTrue(ids.contains(tagRuleA));
    assertFalse(ids.contains(tagRuleB));
  }

  private String seedTag(String tenantId, String namePrefix) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO tags (tag_id, tag_name, tag_color, tag_created_at, tag_updated_at, tenant_id)"
                + " VALUES (?1, ?2, ?3, now(), now(), ?4)")
        .setParameter(1, id)
        .setParameter(2, namePrefix + "-" + UUID.randomUUID())
        .setParameter(3, "#111111")
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
}

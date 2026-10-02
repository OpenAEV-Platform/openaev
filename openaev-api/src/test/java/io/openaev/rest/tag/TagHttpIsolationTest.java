package io.openaev.rest.tag;

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
import io.openaev.rest.tag.form.TagCreateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
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
@TestPropertySource(properties = "openaev.tenant.active-tables=tags")
@WithMockUser(isAdmin = true)
@DisplayName("tags read and write isolation through the real HTTP endpoint")
class TagHttpIsolationTest extends IntegrationTest {

  private static final String TENANT_TAGS = "/api/tenants/{tenantId}/tags";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String tagA;
  private String tagB;

  @BeforeEach
  void seedTwoTenantsWithOneTagEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("tag-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("tag-iso-b").getId();
    tagA = seedTag(tenantA, "tag-a", "#111111");
    tagB = seedTag(tenantB, "tag-b", "#222222");
  }

  @Test
  @DisplayName("under tenant A's path: list returns A's tag and not B's")
  void underTenantAPath() throws Exception {
    String response =
        mvc.perform(get(TENANT_TAGS, tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(response.contains(tagA), "A's tag must appear under tenant A's path");
    assertFalse(response.contains(tagB), "B's tag must not appear under tenant A's path");
  }

  @Test
  @DisplayName("under tenant A's path: search returns A's tag and not B's")
  void searchUnderTenantAReturnsOnlyA() throws Exception {
    String body = asJsonString(PaginationFixture.getDefault().textSearch("").build());
    String response =
        mvc.perform(
                post(TENANT_TAGS + "/search", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertTrue(response.contains(tagA), "A's tag must appear in A's search results");
    assertFalse(response.contains(tagB), "B's tag must not appear in A's search results");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    TagCreateInput input = new TagCreateInput();
    input.setName("created-under-a");
    input.setColor("#abcdef");

    String response =
        mvc.perform(
                post(TENANT_TAGS, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String createdId = JsonPath.read(response, "$.tag_id");
    String storedTenant = rawTenantId(createdId);
    assertEquals(tenantA, storedTenant, "the created tag must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    TagCreateInput input = new TagCreateInput();
    input.setName("no-selector");
    input.setColor("#bbbbbb");

    mvc.perform(
            post("/api/tags")
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("upserting the same name under A and B yields two distinct rows")
  void upsertSameNameUnderTwoTenantsYieldsTwoRows() throws Exception {
    String sharedName = "shared-tag";
    seedTag(tenantB, sharedName, "#123456");

    TagCreateInput input = new TagCreateInput();
    input.setName(sharedName);
    input.setColor("#654321");

    mvc.perform(
            post(TENANT_TAGS + "/upsert", tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(csrf()))
        .andExpect(status().isOk());

    entityManager.flush();
    Long count =
        entityManager
            .unwrap(Session.class)
            .doReturningWork(
                connection -> {
                  try (var stmt =
                      connection.prepareStatement("SELECT count(*) FROM tags WHERE tag_name = ?")) {
                    stmt.setString(1, sharedName);
                    try (var rows = stmt.executeQuery()) {
                      rows.next();
                      return rows.getLong(1);
                    }
                  }
                });

    assertEquals(2L, count, "each tenant must own its own row for the same tag name");
  }

  private String rawTenantId(String tagId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement("SELECT tenant_id FROM tags WHERE tag_id = ?")) {
                stmt.setString(1, tagId);
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
}

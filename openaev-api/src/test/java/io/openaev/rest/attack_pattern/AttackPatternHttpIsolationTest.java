package io.openaev.rest.attack_pattern;

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
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that, with {@code attack_patterns} activated, the tenant scope isolates the
 * table through the real {@link AttackPatternApi} endpoints, on both routes: the {@code
 * /api/tenants/{tenantId}/...} path and the non-prefixed route with an {@code X-Tenant-Ids} header.
 * Reads and writes are covered, because the write path resolves its tenant from the request scope
 * and not from the v1 thread-local any more.
 *
 * <p>Each test stays on a single tenant selector so the per-request scope is set once: re-applying
 * the same scope inside the test transaction is tolerated, changing it would hit the nesting guard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=attack_patterns")
@WithMockUser(isAdmin = true)
@DisplayName("attack_patterns read and write isolation through the real HTTP endpoints")
class AttackPatternHttpIsolationTest extends IntegrationTest {

  private static final String PATTERNS = AttackPatternApi.ATTACK_PATTERN_URI;
  private static final String TENANT_PATTERNS = "/api/tenants/{tenantId}/attack_patterns";
  private static final String TENANT_PATTERN_BY_ID = TENANT_PATTERNS + "/{attackPatternId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String patternA;
  private String patternB;

  @BeforeEach
  void seedTwoTenantsWithOnePatternEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("ap-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("ap-iso-b").getId();
    patternA = seedPattern(tenantA, "pattern-a", "TA9801");
    patternB = seedPattern(tenantB, "pattern-b", "TA9802");
  }

  @Nested
  @DisplayName("Reads on the tenant path route")
  class PathRouteReads {

    @Test
    @DisplayName("given A's path, should expose A's pattern and hide B's")
    void given_tenantAPath_should_exposeOnlyOwnPattern() throws Exception {
      // Arrange / Act / Assert
      mvc.perform(get(TENANT_PATTERN_BY_ID, tenantA, patternA)).andExpect(status().isOk());
      mvc.perform(get(TENANT_PATTERN_BY_ID, tenantA, patternB)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given B's path, should expose B's pattern and hide A's")
    void given_tenantBPath_should_exposeOnlyOwnPattern() throws Exception {
      // Arrange / Act / Assert
      mvc.perform(get(TENANT_PATTERN_BY_ID, tenantB, patternB)).andExpect(status().isOk());
      mvc.perform(get(TENANT_PATTERN_BY_ID, tenantB, patternA)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("given A's path, the raw list should carry A's pattern and not B's")
    void given_tenantAPath_should_listOnlyOwnPattern() throws Exception {
      // Arrange / Act
      String response = body(mvc.perform(get(TENANT_PATTERNS, tenantA)));

      // Assert
      assertTrue(response.contains(patternA), "A's pattern must appear in A's list");
      assertFalse(response.contains(patternB), "B's pattern must not appear in A's list");
    }

    @Test
    @DisplayName("given A's path, the search should return A's pattern and not B's")
    void given_tenantAPath_should_searchOnlyOwnPattern() throws Exception {
      // Arrange
      String input = asJsonString(PaginationFixture.getDefault().size(50).build());

      // Act
      String response =
          body(
              mvc.perform(
                  post(TENANT_PATTERNS + "/search", tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(input)
                      .with(csrf())));

      // Assert
      assertTrue(response.contains(patternA), "A's pattern must appear in A's search results");
      assertFalse(response.contains(patternB), "B's pattern must not appear");
    }

    @Test
    @DisplayName("given A's path, resolving B's id through the options endpoint returns nothing")
    void given_tenantAPath_should_notResolveAnotherTenantIdInOptions() throws Exception {
      // Arrange / Act
      String own =
          body(
              mvc.perform(
                  post(TENANT_PATTERNS + "/options", tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("[\"" + patternA + "\"]")
                      .with(csrf())));
      String foreign =
          body(
              mvc.perform(
                  post(TENANT_PATTERNS + "/options", tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("[\"" + patternB + "\"]")
                      .with(csrf())));

      // Assert
      assertTrue(own.contains(patternA), "A's own id must resolve under A's path");
      assertFalse(foreign.contains(patternB), "B's id must not resolve under A's path");
    }
  }

  @Nested
  @DisplayName("Reads on the X-Tenant-Ids header route")
  class HeaderRouteReads {

    @Test
    @DisplayName("given the A header, the raw list should carry A's pattern and not B's")
    void given_tenantAHeader_should_listOnlyOwnPattern() throws Exception {
      // Arrange / Act
      String response = body(mvc.perform(get(PATTERNS).header("X-Tenant-Ids", tenantA)));

      // Assert
      assertTrue(response.contains(patternA), "A's pattern must appear when A is selected");
      assertFalse(response.contains(patternB), "B's pattern must not appear");
    }

    @Test
    @DisplayName("given the A header, the search should return A's pattern and not B's")
    void given_tenantAHeader_should_searchOnlyOwnPattern() throws Exception {
      // Arrange
      String input = asJsonString(PaginationFixture.getDefault().size(50).build());

      // Act
      String response =
          body(
              mvc.perform(
                  post(PATTERNS + "/search")
                      .header("X-Tenant-Ids", tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(input)
                      .with(csrf())));

      // Assert
      assertTrue(response.contains(patternA), "A's pattern must appear in A's search results");
      assertFalse(response.contains(patternB), "B's pattern must not appear");
    }

    @Test
    @DisplayName("given the A header, reading B's pattern by id is not found")
    void given_tenantAHeader_should_notReadAnotherTenantPatternById() throws Exception {
      // Arrange / Act / Assert
      mvc.perform(get(PATTERNS + "/" + patternA).header("X-Tenant-Ids", tenantA))
          .andExpect(status().isOk());
      mvc.perform(get(PATTERNS + "/" + patternB).header("X-Tenant-Ids", tenantA))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("Writes")
  class Writes {

    @Test
    @DisplayName("given A's path, a create should be attributed to tenant A")
    void given_tenantAPath_should_attributeCreateToA() throws Exception {
      // Arrange / Act
      String response =
          body(
              mvc.perform(
                  post(TENANT_PATTERNS, tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(createInput("created-under-a", "TA9810"))
                      .with(csrf())));

      // Assert
      String createdId = JsonPath.read(response, "$.attack_pattern_id");
      assertEquals(tenantA, rawTenant(createdId), "the created pattern must belong to tenant A");
    }

    @Test
    @DisplayName("given the A header, a create should be attributed to tenant A")
    void given_tenantAHeader_should_attributeCreateToA() throws Exception {
      // Arrange / Act
      String response =
          body(
              mvc.perform(
                  post(PATTERNS)
                      .header("X-Tenant-Ids", tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(createInput("created-via-header-a", "TA9811"))
                      .with(csrf())));

      // Assert
      String createdId = JsonPath.read(response, "$.attack_pattern_id");
      assertEquals(tenantA, rawTenant(createdId), "the created pattern must belong to tenant A");
    }

    @Test
    @DisplayName("given no tenant selector, a create by a multi-tenant caller is refused")
    void given_noSelector_should_refuseCreate() throws Exception {
      // Arrange / Act / Assert
      mvc.perform(
              post(PATTERNS)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(createInput("no-selector", "TA9812"))
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("given the A header, an upsert should be attributed to tenant A")
    void given_tenantAHeader_should_attributeUpsertToA() throws Exception {
      // Arrange / Act
      String response =
          body(
              mvc.perform(
                  post(PATTERNS + "/upsert")
                      .header("X-Tenant-Ids", tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"attack_patterns\":[" + createInput("upserted-a", "TA9813") + "]}")
                      .with(csrf())));

      // Assert
      String createdId = JsonPath.read(response, "$[0].attack_pattern_id");
      assertEquals(tenantA, rawTenant(createdId), "the upserted pattern must belong to tenant A");
    }

    @Test
    @DisplayName("given A's path, an upsert must not adopt another tenant's row on the same id")
    void given_tenantAPath_should_notAdoptAnotherTenantRowOnUpsert() throws Exception {
      // Arrange: B already holds TA9802; A upserts the same external id.
      String response =
          body(
              mvc.perform(
                  post(TENANT_PATTERNS + "/upsert", tenantA)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"attack_patterns\":[" + createInput("a-same-ext-id", "TA9802") + "]}")
                      .with(csrf())));

      // Assert: a new row for A, and B's row untouched.
      String createdId = JsonPath.read(response, "$[0].attack_pattern_id");
      assertEquals(tenantA, rawTenant(createdId), "A's upsert must create a row owned by A");
      assertFalse(createdId.equals(patternB), "A's upsert must not reuse B's row");
      assertEquals("pattern-b", rawName(patternB), "B's row must be untouched");
    }

    @Test
    @DisplayName("given A's path, A can update its own pattern")
    void given_tenantAPath_should_updateOwnPattern() throws Exception {
      // Arrange / Act
      mvc.perform(
              put(TENANT_PATTERN_BY_ID, tenantA, patternA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(updateInput("renamed-a", "TA9801"))
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertEquals("renamed-a", rawName(patternA), "A's own pattern must be updated");
    }

    @Test
    @DisplayName("given A's path, updating B's pattern is not found and leaves it untouched")
    void given_tenantAPath_should_notUpdateAnotherTenantPattern() throws Exception {
      // Arrange / Act
      mvc.perform(
              put(TENANT_PATTERN_BY_ID, tenantA, patternB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(updateInput("hijacked", "TA9802"))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals("pattern-b", rawName(patternB), "B's pattern must be untouched");
    }

    @Test
    @DisplayName("given the A header, updating B's pattern is not found and leaves it untouched")
    void given_tenantAHeader_should_notUpdateAnotherTenantPattern() throws Exception {
      // Arrange / Act
      mvc.perform(
              put(PATTERNS + "/" + patternB)
                  .header("X-Tenant-Ids", tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(updateInput("hijacked", "TA9802"))
                  .with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals("pattern-b", rawName(patternB), "B's pattern must be untouched");
    }

    @Test
    @DisplayName("given A's path, A can delete its own pattern")
    void given_tenantAPath_should_deleteOwnPattern() throws Exception {
      // Arrange / Act
      mvc.perform(delete(TENANT_PATTERN_BY_ID, tenantA, patternA).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertEquals(0L, rawCount(patternA), "A's own pattern must be deleted");
    }

    @Test
    @DisplayName("given A's path, deleting B's pattern is a no-op and leaves it in place")
    void given_tenantAPath_should_notDeleteAnotherTenantPattern() throws Exception {
      // Arrange / Act
      mvc.perform(delete(TENANT_PATTERN_BY_ID, tenantA, patternB).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertEquals(1L, rawCount(patternB), "B's pattern must survive tenant A's delete attempt");
    }

    @Test
    @DisplayName("given the A header, deleting B's pattern is a no-op and leaves it in place")
    void given_tenantAHeader_should_notDeleteAnotherTenantPattern() throws Exception {
      // Arrange / Act
      mvc.perform(delete(PATTERNS + "/" + patternB).header("X-Tenant-Ids", tenantA).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      // Assert
      assertEquals(1L, rawCount(patternB), "B's pattern must survive tenant A's delete attempt");
    }
  }

  private static String createInput(String name, String externalId) {
    return "{\"attack_pattern_name\":\""
        + name
        + "\",\"attack_pattern_external_id\":\""
        + externalId
        + "\",\"attack_pattern_stix_id\":\"attack-pattern--"
        + UUID.randomUUID()
        + "\"}";
  }

  private static String updateInput(String name, String externalId) {
    return "{\"attack_pattern_name\":\""
        + name
        + "\",\"attack_pattern_external_id\":\""
        + externalId
        + "\"}";
  }

  private static String body(org.springframework.test.web.servlet.ResultActions actions)
      throws Exception {
    return actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
  }

  // Ground-truth reads, bypassing the scope: raw JDBC on the test's own connection sees the
  // uncommitted seed and the rewriter does not touch a statement it never generated. A flush first
  // forces any pending scoped UPDATE/DELETE to reach the database.
  private String rawName(String attackPatternId) {
    return rawQuery(
        "SELECT attack_pattern_name FROM attack_patterns WHERE attack_pattern_id = ?",
        statement -> statement.setString(1, attackPatternId),
        rows -> rows.next() ? rows.getString(1) : null);
  }

  private String rawTenant(String attackPatternId) {
    return rawQuery(
        "SELECT tenant_id FROM attack_patterns WHERE attack_pattern_id = ?",
        statement -> statement.setString(1, attackPatternId),
        rows -> rows.next() ? rows.getString(1) : null);
  }

  private long rawCount(String attackPatternId) {
    return rawQuery(
        "SELECT count(*) FROM attack_patterns WHERE attack_pattern_id = ?",
        statement -> statement.setString(1, attackPatternId),
        rows -> {
          rows.next();
          return rows.getLong(1);
        });
  }

  private <T> T rawQuery(String sql, StatementBinder binder, ResultReader<T> reader) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                binder.bind(statement);
                try (ResultSet rows = statement.executeQuery()) {
                  return reader.read(rows);
                }
              }
            });
  }

  private interface StatementBinder {
    void bind(PreparedStatement statement) throws java.sql.SQLException;
  }

  private interface ResultReader<T> {
    T read(ResultSet rows) throws java.sql.SQLException;
  }

  private String seedPattern(String tenantId, String name, String externalId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO attack_patterns"
                + " (attack_pattern_id, attack_pattern_name, attack_pattern_external_id,"
                + "  attack_pattern_stix_id, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4, ?5)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, externalId)
        .setParameter(4, "attack-pattern--" + UUID.randomUUID())
        .setParameter(5, tenantId)
        .executeUpdate();
    return id;
  }
}

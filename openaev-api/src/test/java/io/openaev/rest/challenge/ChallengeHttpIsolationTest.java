package io.openaev.rest.challenge;

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
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
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
 * End-to-end proof that, with {@code challenges} activated, the tenant scope set from the URL path
 * isolates the table through the real {@link ChallengeApi} endpoints. A user who belongs to two
 * tenants sees a challenge only under its own tenant's path, never another tenant's.
 *
 * <p>Each test stays on a single tenant path so the per-request scope is set once: re-applying the
 * same scope inside the test transaction is tolerated, changing it would hit the nesting guard.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=challenges")
@WithMockUser(isAdmin = true)
@DisplayName("challenges read and write isolation through the real HTTP endpoint")
class ChallengeHttpIsolationTest extends IntegrationTest {

  private static final String CHALLENGES = "/api/challenges";
  private static final String TENANT_CHALLENGES = "/api/tenants/{tenantId}/challenges";
  private static final String TENANT_CHALLENGE_BY_ID = TENANT_CHALLENGES + "/{challengeId}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String challengeA;
  private String challengeB;

  @BeforeEach
  void seedTwoTenantsWithOneChallengeEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("challenge-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("challenge-iso-b").getId();
    challengeA = seedChallenge(tenantA, "challenge-a");
    challengeB = seedChallenge(tenantB, "challenge-b");
  }

  @Test
  @DisplayName("under tenant A's path: the list returns A's challenge and not B's")
  void listUnderTenantAReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(TENANT_CHALLENGES, tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(challengeA), "A's challenge must appear in A's list");
    assertFalse(response.contains(challengeB), "B's challenge must not appear in A's list");
  }

  @Test
  @DisplayName(
      "via the X-Tenant-Ids header (no path tenant): the list returns A's challenge and not B's")
  void listViaHeaderReturnsOnlyA() throws Exception {
    String response =
        mvc.perform(get(CHALLENGES).header("X-Tenant-Ids", tenantA))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(
        response.contains(challengeA), "A's challenge must appear when A is selected via header");
    assertFalse(response.contains(challengeB), "B's challenge must not appear");
  }

  @Test
  @DisplayName("under tenant A's path: find returns A's challenge and not B's")
  void findUnderTenantAReturnsOnlyA() throws Exception {
    String body = asJsonString(List.of(challengeA, challengeB));
    String response =
        mvc.perform(
                post(TENANT_CHALLENGES + "/find", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(response.contains(challengeA), "A's challenge must appear in A's find results");
    assertFalse(response.contains(challengeB), "B's challenge must not appear in A's find results");
  }

  @Test
  @DisplayName("a create under tenant A's path is attributed to tenant A")
  void createUnderTenantAIsAttributedToA() throws Exception {
    String response =
        mvc.perform(
                post(TENANT_CHALLENGES, tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createInput("created-under-a"))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String createdId = JsonPath.read(response, "$.challenge_id");
    String storedTenant =
        (String)
            entityManager
                .createNativeQuery("SELECT tenant_id FROM challenges WHERE challenge_id = ?1")
                .setParameter(1, createdId)
                .getSingleResult();
    assertEquals(tenantA, storedTenant, "the created challenge must belong to tenant A");
  }

  @Test
  @DisplayName("a create with no tenant selector is refused (a single-tenant scope is required)")
  void createWithoutSelectorIsRejected() throws Exception {
    mvc.perform(
            post(CHALLENGES)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createInput("no-selector"))
                .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("under tenant A's path: A can update its own challenge")
  void updateUnderTenantAUpdatesOwnChallenge() throws Exception {
    mvc.perform(
            put(TENANT_CHALLENGE_BY_ID, tenantA, challengeA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createInput("renamed-a"))
                .with(csrf()))
        .andExpect(status().isOk());
    assertEquals("renamed-a", rawName(challengeA), "A's own challenge must be updated");
  }

  @Test
  @DisplayName("under tenant A's path: updating B's challenge is not found and leaves it untouched")
  void updateUnderTenantAOfBChallengeIsBlocked() throws Exception {
    mvc.perform(
            put(TENANT_CHALLENGE_BY_ID, tenantA, challengeB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createInput("hijacked"))
                .with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals("challenge-b", rawName(challengeB), "B's challenge must be untouched");
  }

  @Test
  @DisplayName("under tenant A's path: A can delete its own challenge")
  void deleteUnderTenantADeletesOwnChallenge() throws Exception {
    mvc.perform(delete(TENANT_CHALLENGE_BY_ID, tenantA, challengeA).with(csrf()))
        .andExpect(status().is2xxSuccessful());
    assertEquals(0L, rawCount(challengeA), "A's own challenge must be deleted");
  }

  @Test
  @DisplayName("under tenant A's path: deleting B's challenge is a no-op and leaves it in place")
  void deleteUnderTenantAOfBChallengeIsBlocked() throws Exception {
    mvc.perform(delete(TENANT_CHALLENGE_BY_ID, tenantA, challengeB).with(csrf()))
        .andExpect(status().isNotFound());
    assertEquals(1L, rawCount(challengeB), "B's challenge must survive tenant A's delete attempt");
  }

  private static String createInput(String name) {
    return "{\"challenge_name\":\""
        + name
        + "\",\"challenge_category\":\"cat\",\"challenge_content\":\"content\","
        + "\"challenge_score\":10,\"challenge_max_attempts\":3,\"challenge_tags\":[],"
        + "\"challenge_documents\":[],\"challenge_flags\":[{\"flag_type\":\"VALUE\",\"flag_value\":\"x\"}]}";
  }

  // Ground-truth reads, bypassing the scope: raw JDBC on the test's own connection sees the
  // uncommitted seed and the rewriter does not touch a statement it never generated. A flush first
  // forces any pending scoped UPDATE/DELETE to reach the database.
  private String rawName(String challengeId) {
    return rawQuery(
        "SELECT challenge_name FROM challenges WHERE challenge_id = ?",
        statement -> statement.setString(1, challengeId),
        rows -> rows.next() ? rows.getString(1) : null);
  }

  private long rawCount(String challengeId) {
    return rawQuery(
        "SELECT count(*) FROM challenges WHERE challenge_id = ?",
        statement -> statement.setString(1, challengeId),
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

  private String seedChallenge(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO challenges"
                + " (challenge_id, challenge_name, challenge_category, challenge_content,"
                + "  challenge_score, challenge_max_attempts, tenant_id)"
                + " VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)")
        .setParameter(1, id)
        .setParameter(2, name)
        .setParameter(3, "cat")
        .setParameter(4, "content")
        .setParameter(5, 10.0)
        .setParameter(6, 3)
        .setParameter(7, tenantId)
        .executeUpdate();
    return id;
  }
}

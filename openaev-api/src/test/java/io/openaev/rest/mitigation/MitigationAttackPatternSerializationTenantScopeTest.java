package io.openaev.rest.mitigation;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.utils.JsonTestUtils;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * With {@code attack_patterns} on v2 isolation, the mitigation read endpoints return the raw {@code
 * Mitigation} entity and serialize its {@code mitigations_attack_patterns} lazy {@code @ManyToMany}
 * open-in-view, AFTER the controller transaction has committed. {@code
 * TenantScopeTransactionAspect} sets {@code app.current_tenants} only for the duration of that
 * transaction, so a post-commit lazy load runs unscoped and fails closed to an empty array, and an
 * in-scope link disappears from the response. {@code AttackPatternInitializer} force-initializes
 * the association inside the scoped transaction to prevent it.
 *
 * <p>All three read shapes are covered, because they hydrate independently and the search endpoint
 * was the one that did not: the list, the paginated search, and the by-id detail.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the transaction-local GUC would stay alive through serialization and MASK the very
 * failure this pins. Seeding therefore goes through an auto-committing {@link JdbcTemplate} and is
 * removed on teardown.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=attack_patterns")
@WithMockUser(isAdmin = true)
@DisplayName(
    "Mitigation read endpoints serialize their attack patterns with attack_patterns active")
class MitigationAttackPatternSerializationTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String mitigationId;
  private String attackPatternId;
  private final List<String> seededMitigations = new ArrayList<>();
  private final List<String> seededAttackPatterns = new ArrayList<>();

  @BeforeEach
  void seedACommittedMitigationCarryingOneAttackPattern() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    String tenant = tenantHelper.createTenantWithCurrentUser("mitigation-ap-sink").getId();
    attackPatternId = seedAttackPattern(tenant);
    mitigationId = seedMitigation(tenant);
    jdbc.update(
        "INSERT INTO mitigations_attack_patterns (mitigation_id, attack_pattern_id)"
            + " VALUES (?, ?)",
        mitigationId,
        attackPatternId);
  }

  @AfterEach
  void cleanup() {
    for (String id : seededMitigations) {
      jdbc.update("DELETE FROM mitigations_attack_patterns WHERE mitigation_id = ?", id);
      jdbc.update("DELETE FROM mitigations WHERE mitigation_id = ?", id);
    }
    for (String id : seededAttackPatterns) {
      jdbc.update("DELETE FROM mitigations_attack_patterns WHERE attack_pattern_id = ?", id);
      jdbc.update("DELETE FROM attack_patterns WHERE attack_pattern_id = ?", id);
    }
    seededMitigations.clear();
    seededAttackPatterns.clear();
  }

  @Test
  @DisplayName("the list keeps the link after the transaction commits")
  void given_anInScopeLink_when_listingMitigations_then_theLinkSurvivesSerialization()
      throws Exception {
    mvc.perform(get("/api/mitigations").accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].mitigation_attack_patterns[*]").value(hasItem(attackPatternId)));
  }

  @Test
  @DisplayName("the paginated search keeps the link after the transaction commits")
  void given_anInScopeLink_when_searchingMitigations_then_theLinkSurvivesSerialization()
      throws Exception {
    String body = JsonTestUtils.asJsonString(PaginationFixture.getDefault().textSearch("").build());
    mvc.perform(
            post("/api/mitigations/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.content[*].mitigation_attack_patterns[*]").value(hasItem(attackPatternId)));
  }

  @Test
  @DisplayName("the by-id detail keeps the link after the transaction commits")
  void given_anInScopeLink_when_readingOneMitigation_then_theLinkSurvivesSerialization()
      throws Exception {
    mvc.perform(
            get("/api/mitigations/{id}", mitigationId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mitigation_attack_patterns").value(hasItem(attackPatternId)));
  }

  private String seedMitigation(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO mitigations (mitigation_id, mitigation_name, mitigation_external_id,"
            + " mitigation_stix_id, tenant_id) VALUES (?, ?, ?, ?, ?)",
        id,
        "mitigation-" + id,
        "M" + id.substring(0, 8),
        "course-of-action--" + UUID.randomUUID(),
        tenantId);
    seededMitigations.add(id);
    return id;
  }

  private String seedAttackPattern(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO attack_patterns (attack_pattern_id, attack_pattern_name,"
            + " attack_pattern_external_id, attack_pattern_stix_id, tenant_id)"
            + " VALUES (?, ?, ?, ?, ?)",
        id,
        "pattern-" + id,
        "T" + id.substring(0, 8),
        "attack-pattern--" + UUID.randomUUID(),
        tenantId);
    seededAttackPatterns.add(id);
    return id;
  }
}

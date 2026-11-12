package io.openaev.rest.attack_pattern;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.attack_pattern.AttackPatternApi.ATTACK_PATTERN_URI;
import static io.openaev.rest.mitigation.MitigationApi.MITIGATION_URI;
import static io.openaev.rest.mitigation.MitigationApi.TENANT_MITIGATION_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The kill chain phases named in the body of an attack pattern update are read in the tenant of the
 * attack pattern, and the attack patterns named in the body of a mitigation update are read in the
 * tenant of the mitigation. An id of a different tenant is treated as an unknown id: {@code
 * findAllById} skips it.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so each request runs its own transaction with its own
 * tenant scope, as in production, and they are removed on teardown.
 *
 * <p>Header cases select the default tenant and a second one through {@code X-Tenant-Ids}; the
 * owner is the second tenant's, so the default tenant's rows are the foreign ones.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Kill chain phases and attack patterns are linked within the tenant of their owner")
class AttackPatternLinksTenantScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String TENANT_ATTACK_PATTERN_URI = TENANT_PREFIX + "/attack_patterns";
  // A null id list is refused with an IllegalArgumentException, which RestBehavior answers with a
  // 400.
  private static final String NULL_LIST_OUTCOME = "status 400";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantB;
  private final List<Object[]> deletes = new ArrayList<>();
  private final List<Object[]> firstDeletes = new ArrayList<>();
  private final List<String[]> seeded = new ArrayList<>();

  @BeforeEach
  void seedTenants() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // The caller is a member of the default tenant first, then of a tenant of its own.
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
    tenantB = tenantHelper.createTenantWithCurrentUser("attack-pattern-links").getId();
    assertNotEquals(DEFAULT_TENANT, tenantB);
  }

  @AfterEach
  void cleanup() {
    for (Object[] delete : firstDeletes) {
      jdbc.update((String) delete[0], Arrays.copyOfRange(delete, 1, delete.length));
    }
    firstDeletes.clear();
    // Reverse insertion order: links and children before the rows they reference.
    for (int i = deletes.size() - 1; i >= 0; i--) {
      Object[] delete = deletes.get(i);
      jdbc.update((String) delete[0], Arrays.copyOfRange(delete, 1, delete.length));
    }
    deletes.clear();
    seeded.clear();
    tenantHelper.deleteCommittedTenants(tenantB);
  }

  @Nested
  @DisplayName("Kill chain phases of an attack pattern")
  class KillChainPhasesOfAttackPattern {

    @Test
    @DisplayName(
        "given a phase of each tenant when updating through the header then only the owner's is linked")
    void given_phasesOfTwoTenants_should_linkOnlyOwnerPhaseThroughHeader() throws Exception {
      // Arrange
      String attackPatternId = seedAttackPattern(tenantB);
      String ownPhase = seedPhase(tenantB);
      String foreignPhase = seedPhase(DEFAULT_TENANT);
      arrangeHeaderRoute();

      // Act
      ResultActions result =
          updateAttackPattern(attackPatternId, List.of(ownPhase, foreignPhase), false);

      // Assert
      result.andExpect(status().isOk());
      assertThat(phasesOf(attackPatternId)).containsExactly(ownPhase);
    }

    @Test
    @DisplayName(
        "given a phase of the owner's tenant when updating through the header then it is linked")
    void given_ownPhase_should_linkPhaseThroughHeader() throws Exception {
      // Arrange
      String attackPatternId = seedAttackPattern(tenantB);
      String ownPhase = seedPhase(tenantB);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateAttackPattern(attackPatternId, List.of(ownPhase), false);

      // Assert
      result.andExpect(status().isOk());
      assertThat(phasesOf(attackPatternId)).containsExactly(ownPhase);
    }

    @Test
    @DisplayName(
        "given a phase of the owner's tenant when updating through the tenant path then it is linked")
    void given_ownPhase_should_linkPhaseThroughTenantPath() throws Exception {
      // Arrange
      String attackPatternId = seedAttackPattern(tenantB);
      String ownPhase = seedPhase(tenantB);
      assertSeededTenants();

      // Act
      ResultActions result = updateAttackPattern(attackPatternId, List.of(ownPhase), true);

      // Assert
      result.andExpect(status().isOk());
      assertThat(phasesOf(attackPatternId)).containsExactly(ownPhase);
    }

    @Test
    @DisplayName(
        "given a null phase list when updating through the header then it is refused and the link stays")
    void given_nullPhaseList_should_refuseAndKeepLinkThroughHeader() throws Exception {
      // Arrange
      String attackPatternId = seedAttackPattern(tenantB);
      String ownPhase = seedPhase(tenantB);
      linkPhase(attackPatternId, ownPhase);
      arrangeHeaderRoute();

      // Act
      String outcome = outcomeOf(() -> updateAttackPattern(attackPatternId, null, false));

      // Assert
      assertEquals(NULL_LIST_OUTCOME, outcome);
      assertThat(phasesOf(attackPatternId)).containsExactly(ownPhase);
    }
  }

  @Nested
  @DisplayName("Attack patterns of a mitigation")
  class AttackPatternsOfMitigation {

    @Test
    @DisplayName(
        "given an attack pattern of each tenant when updating through the header then only the owner's is linked")
    void given_attackPatternsOfTwoTenants_should_linkOnlyOwnerPatternThroughHeader()
        throws Exception {
      // Arrange
      String mitigationId = seedMitigation(tenantB);
      String ownPattern = seedAttackPattern(tenantB);
      String foreignPattern = seedAttackPattern(DEFAULT_TENANT);
      arrangeHeaderRoute();

      // Act
      ResultActions result =
          updateMitigation(mitigationId, List.of(ownPattern, foreignPattern), false);

      // Assert
      result.andExpect(status().isOk());
      assertThat(patternsOf(mitigationId)).containsExactly(ownPattern);
    }

    @Test
    @DisplayName(
        "given an attack pattern of the owner's tenant when updating through the header then it is linked")
    void given_ownAttackPattern_should_linkPatternThroughHeader() throws Exception {
      // Arrange
      String mitigationId = seedMitigation(tenantB);
      String ownPattern = seedAttackPattern(tenantB);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateMitigation(mitigationId, List.of(ownPattern), false);

      // Assert
      result.andExpect(status().isOk());
      assertThat(patternsOf(mitigationId)).containsExactly(ownPattern);
    }

    @Test
    @DisplayName(
        "given an attack pattern of the owner's tenant when updating through the tenant path then it is linked")
    void given_ownAttackPattern_should_linkPatternThroughTenantPath() throws Exception {
      // Arrange
      String mitigationId = seedMitigation(tenantB);
      String ownPattern = seedAttackPattern(tenantB);
      assertSeededTenants();

      // Act
      ResultActions result = updateMitigation(mitigationId, List.of(ownPattern), true);

      // Assert
      result.andExpect(status().isOk());
      assertThat(patternsOf(mitigationId)).containsExactly(ownPattern);
    }

    @Test
    @DisplayName(
        "given a null attack pattern list when updating through the header then it is refused and the link stays")
    void given_nullAttackPatternList_should_refuseAndKeepLinkThroughHeader() throws Exception {
      // Arrange
      String mitigationId = seedMitigation(tenantB);
      String ownPattern = seedAttackPattern(tenantB);
      linkPattern(mitigationId, ownPattern);
      arrangeHeaderRoute();

      // Act
      String outcome = outcomeOf(() -> updateMitigation(mitigationId, null, false));

      // Assert
      assertEquals(NULL_LIST_OUTCOME, outcome);
      assertThat(patternsOf(mitigationId)).containsExactly(ownPattern);
    }
  }

  // -- Requests --

  private interface Request {
    ResultActions perform() throws Exception;
  }

  /** The status of the response, or the class and cause of the exception thrown. */
  private static String outcomeOf(Request request) {
    try {
      MockHttpServletResponse response = request.perform().andReturn().getResponse();
      return "status " + response.getStatus();
    } catch (Exception e) {
      Throwable cause = e.getCause();
      return e.getClass().getName()
          + (cause == null ? "" : " <- " + cause.getClass().getName() + ": " + cause.getMessage());
    }
  }

  private ResultActions updateAttackPattern(
      String attackPatternId, List<String> phaseIds, boolean tenantRoute) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("attack_pattern_name", "attack-pattern-links-" + attackPatternId);
    body.put("attack_pattern_external_id", externalIdOf("attack_patterns", attackPatternId));
    body.put("attack_pattern_kill_chain_phases", phaseIds);
    MockHttpServletRequestBuilder request =
        tenantRoute
            ? put(TENANT_ATTACK_PATTERN_URI + "/{attackPatternId}", tenantB, attackPatternId)
            : put(ATTACK_PATTERN_URI + "/{attackPatternId}", attackPatternId)
                .header(TENANT_HEADER, scopeHeader());
    return perform(request, body);
  }

  private ResultActions updateMitigation(
      String mitigationId, List<String> attackPatternIds, boolean tenantRoute) throws Exception {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("mitigation_name", "attack-pattern-links-" + mitigationId);
    body.put("mitigation_external_id", externalIdOf("mitigations", mitigationId));
    body.put("mitigation_attack_patterns", attackPatternIds);
    MockHttpServletRequestBuilder request =
        tenantRoute
            ? put(TENANT_MITIGATION_URI + "/{mitigationId}", tenantB, mitigationId)
            : put(MITIGATION_URI + "/{mitigationId}", mitigationId)
                .header(TENANT_HEADER, scopeHeader());
    return perform(request, body);
  }

  private ResultActions perform(MockHttpServletRequestBuilder request, Map<String, Object> body)
      throws Exception {
    return mvc.perform(
        request
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(asJsonString(body))
            .with(csrf()));
  }

  private String scopeHeader() {
    return DEFAULT_TENANT + "," + tenantB;
  }

  private void arrangeHeaderRoute() {
    TenantContext.clearCurrentTenant();
    assertEquals(DEFAULT_TENANT, TenantContext.getCurrentTenant());
    assertSeededTenants();
  }

  private void assertSeededTenants() {
    for (String[] row : seeded) {
      String actual =
          jdbc.queryForObject(
              "SELECT tenant_id FROM " + row[0] + " WHERE " + row[1] + " = ?",
              String.class,
              row[2]);
      assertEquals(row[3], actual, row[0] + " " + row[2]);
    }
  }

  // -- Reads --

  private String externalIdOf(String table, String id) {
    String prefix = table.equals("attack_patterns") ? "attack_pattern" : "mitigation";
    return jdbc.queryForObject(
        "SELECT " + prefix + "_external_id FROM " + table + " WHERE " + prefix + "_id = ?",
        String.class,
        id);
  }

  private List<String> phasesOf(String attackPatternId) {
    return jdbc.queryForList(
        "SELECT phase_id FROM attack_patterns_kill_chain_phases WHERE attack_pattern_id = ?"
            + " ORDER BY phase_id",
        String.class,
        attackPatternId);
  }

  private List<String> patternsOf(String mitigationId) {
    return jdbc.queryForList(
        "SELECT attack_pattern_id FROM mitigations_attack_patterns WHERE mitigation_id = ?"
            + " ORDER BY attack_pattern_id",
        String.class,
        mitigationId);
  }

  // -- Seeds --

  private String seedAttackPattern(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO attack_patterns (attack_pattern_id, attack_pattern_stix_id,"
            + " attack_pattern_name, attack_pattern_external_id, tenant_id)"
            + " VALUES (?, ?, ?, ?, ?)",
        id,
        "attack-pattern--" + id,
        "attack-pattern-links-" + id,
        "T-" + id,
        tenantId);
    trackDelete("DELETE FROM attack_patterns WHERE attack_pattern_id = ?", id);
    trackFirst("DELETE FROM attack_patterns_kill_chain_phases WHERE attack_pattern_id = ?", id);
    trackFirst("DELETE FROM mitigations_attack_patterns WHERE attack_pattern_id = ?", id);
    seeded.add(new String[] {"attack_patterns", "attack_pattern_id", id, tenantId});
    return id;
  }

  private String seedPhase(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO kill_chain_phases (phase_id, phase_name, phase_shortname,"
            + " phase_kill_chain_name, phase_external_id, phase_order, tenant_id)"
            + " VALUES (?, ?, ?, 'mitre-attack', ?, 1, ?)",
        id,
        "attack-pattern-links-" + id,
        id,
        id,
        tenantId);
    trackDelete("DELETE FROM kill_chain_phases WHERE phase_id = ?", id);
    trackFirst("DELETE FROM attack_patterns_kill_chain_phases WHERE phase_id = ?", id);
    seeded.add(new String[] {"kill_chain_phases", "phase_id", id, tenantId});
    return id;
  }

  private String seedMitigation(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO mitigations (mitigation_id, mitigation_name, mitigation_external_id,"
            + " mitigation_stix_id, tenant_id) VALUES (?, ?, ?, ?, ?)",
        id,
        "attack-pattern-links-" + id,
        "M-" + id,
        "course-of-action--" + id,
        tenantId);
    trackDelete("DELETE FROM mitigations WHERE mitigation_id = ?", id);
    trackFirst("DELETE FROM mitigations_attack_patterns WHERE mitigation_id = ?", id);
    seeded.add(new String[] {"mitigations", "mitigation_id", id, tenantId});
    return id;
  }

  private void linkPhase(String attackPatternId, String phaseId) {
    jdbc.update(
        "INSERT INTO attack_patterns_kill_chain_phases (attack_pattern_id, phase_id)"
            + " VALUES (?, ?)",
        attackPatternId,
        phaseId);
  }

  private void linkPattern(String mitigationId, String attackPatternId) {
    jdbc.update(
        "INSERT INTO mitigations_attack_patterns (mitigation_id, attack_pattern_id)"
            + " VALUES (?, ?)",
        mitigationId,
        attackPatternId);
  }

  private void trackDelete(String sql, Object... params) {
    deletes.add(statement(sql, params));
  }

  private void trackFirst(String sql, Object... params) {
    firstDeletes.add(statement(sql, params));
  }

  private static Object[] statement(String sql, Object[] params) {
    Object[] statement = new Object[params.length + 1];
    System.arraycopy(params, 0, statement, 1, params.length);
    statement[0] = sql;
    return statement;
  }
}

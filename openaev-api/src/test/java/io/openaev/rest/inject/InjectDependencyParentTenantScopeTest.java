package io.openaev.rest.inject;

import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.rest.inject.InjectApi.TENANT_INJECT_URI;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The parent inject named in the {@code inject_depends_on} body of a simulation inject update is
 * read in the tenant of the inject written. A parent of another tenant is answered like an unknown
 * id: 404, and no dependency is written.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the rows are committed through an
 * auto-committing {@link JdbcTemplate}, so each request runs its own transaction with its own
 * tenant scope, as in production, and they are removed on teardown.
 *
 * <p>Header cases select the default tenant and a second one through {@code X-Tenant-Ids}, so both
 * parents are inside the request scope; the simulation and its inject are the default tenant's, so
 * the second tenant's inject is the foreign parent.
 */
@WithMockUser(isAdmin = true)
@DisplayName("The parent of an inject dependency is resolved in the tenant of the inject written")
class InjectDependencyParentTenantScopeTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

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

  private record SeededInject(String simulationId, String injectId) {}

  @BeforeEach
  void seedTenants() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    // The caller is a member of the default tenant first, then of a tenant of its own.
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
    tenantB = tenantHelper.createTenantWithCurrentUser("inject-dependency-parent").getId();
    assertNotEquals(DEFAULT_TENANT, tenantB);
  }

  @AfterEach
  void cleanup() {
    for (Object[] delete : firstDeletes) {
      jdbc.update((String) delete[0], Arrays.copyOfRange(delete, 1, delete.length));
    }
    firstDeletes.clear();
    // Reverse insertion order: children before the rows they reference.
    for (int i = deletes.size() - 1; i >= 0; i--) {
      Object[] delete = deletes.get(i);
      jdbc.update((String) delete[0], Arrays.copyOfRange(delete, 1, delete.length));
    }
    deletes.clear();
    seeded.clear();
    tenantHelper.deleteCommittedTenants(tenantB);
  }

  @Nested
  @DisplayName("Through the header route")
  class ThroughHeaderRoute {

    @Test
    @DisplayName(
        "given a parent of another tenant of the scope when updating the inject then it is not found and nothing is linked")
    void given_parentOfOtherTenantInScope_should_answerNotFoundAndLinkNothing() throws Exception {
      // Arrange
      SeededInject inject = seedInject(DEFAULT_TENANT);
      SeededInject foreignParent = seedInject(tenantB);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateDependsOn(inject, foreignParent.injectId(), false);

      // Assert
      result.andExpect(status().isNotFound());
      assertThat(parentsOf(inject.injectId())).isEmpty();
    }

    @Test
    @DisplayName(
        "given a parent of the inject's tenant when updating the inject then the dependency is linked")
    void given_parentOfInjectTenant_should_linkDependency() throws Exception {
      // Arrange
      SeededInject inject = seedInject(DEFAULT_TENANT);
      SeededInject ownParent = seedInject(DEFAULT_TENANT);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateDependsOn(inject, ownParent.injectId(), false);

      // Assert
      result.andExpect(status().isOk());
      assertThat(parentsOf(inject.injectId())).containsExactly(ownParent.injectId());
    }

    @Test
    @DisplayName(
        "given an unknown parent id when updating the inject then it is not found and nothing is linked")
    void given_unknownParent_should_answerNotFoundAndLinkNothing() throws Exception {
      // Arrange
      SeededInject inject = seedInject(DEFAULT_TENANT);
      arrangeHeaderRoute();

      // Act
      ResultActions result = updateDependsOn(inject, UUID.randomUUID().toString(), false);

      // Assert
      result.andExpect(status().isNotFound());
      assertThat(parentsOf(inject.injectId())).isEmpty();
    }
  }

  @Nested
  @DisplayName("Through the tenant route")
  class ThroughTenantRoute {

    @Test
    @DisplayName(
        "given a parent of the inject's tenant when updating through the tenant path then the dependency is linked")
    void given_parentOfInjectTenant_should_linkDependencyThroughTenantPath() throws Exception {
      // Arrange
      SeededInject inject = seedInject(tenantB);
      SeededInject ownParent = seedInject(tenantB);
      assertSeededTenants();

      // Act
      ResultActions result = updateDependsOn(inject, ownParent.injectId(), true);

      // Assert
      result.andExpect(status().isOk());
      assertThat(parentsOf(inject.injectId())).containsExactly(ownParent.injectId());
    }

    @Test
    @DisplayName(
        "given a parent of another tenant when updating through the tenant path then it is not found and nothing is linked")
    void given_parentOfOtherTenant_should_answerNotFoundThroughTenantPath() throws Exception {
      // Arrange
      SeededInject inject = seedInject(tenantB);
      SeededInject foreignParent = seedInject(DEFAULT_TENANT);
      assertSeededTenants();

      // Act
      ResultActions result = updateDependsOn(inject, foreignParent.injectId(), true);

      // Assert
      result.andExpect(status().isNotFound());
      assertThat(parentsOf(inject.injectId())).isEmpty();
    }
  }

  // -- Requests --

  private ResultActions updateDependsOn(
      SeededInject inject, String parentInjectId, boolean tenantRoute) throws Exception {
    Map<String, Object> condition = new LinkedHashMap<>();
    condition.put("key", "Execution");
    condition.put("value", true);
    condition.put("operator", "eq");
    Map<String, Object> dependency = new LinkedHashMap<>();
    dependency.put(
        "dependency_relationship",
        Map.of("inject_parent_id", parentInjectId, "inject_children_id", inject.injectId()));
    dependency.put("dependency_condition", Map.of("mode", "and", "conditions", List.of(condition)));
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("inject_title", "inject-dependency-parent-" + inject.injectId());
    body.put("inject_depends_duration", 0);
    body.put("inject_depends_on", List.of(dependency));
    MockHttpServletRequestBuilder request =
        tenantRoute
            ? put(
                TENANT_INJECT_URI + "/{exerciseId}/{injectId}",
                tenantB,
                inject.simulationId(),
                inject.injectId())
            : put(INJECT_URI + "/{exerciseId}/{injectId}", inject.simulationId(), inject.injectId())
                .header(TENANT_HEADER, DEFAULT_TENANT + "," + tenantB);
    return mvc.perform(
        request
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .content(asJsonString(body))
            .with(csrf()));
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

  private List<String> parentsOf(String injectId) {
    return jdbc.queryForList(
        "SELECT inject_parent_id FROM injects_dependencies WHERE inject_children_id = ?",
        String.class,
        injectId);
  }

  // -- Seeds --

  /** A simulation and one of its injects, both of the given tenant, with a contract of it. */
  private SeededInject seedInject(String tenantId) {
    String simulationId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
            + " VALUES (?, ?, 'SCHEDULED', ?)",
        simulationId,
        "inject-dependency-parent-" + simulationId,
        tenantId);
    trackDelete("DELETE FROM exercises WHERE exercise_id = ?", simulationId);
    seeded.add(new String[] {"exercises", "exercise_id", simulationId, tenantId});

    String contractId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, tenant_id,"
            + " injector_contract_content, injector_contract_import_available)"
            + " VALUES (?, ?, '{\"fields\": []}', false)",
        contractId,
        tenantId);
    trackDelete("DELETE FROM injectors_contracts WHERE injector_contract_id = ?", contractId);
    seeded.add(new String[] {"injectors_contracts", "injector_contract_id", contractId, tenantId});

    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, tenant_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_injector_contract, inject_exercise)"
            + " VALUES (?, ?, ?, false, true, 0, ?, ?)",
        injectId,
        tenantId,
        "inject-dependency-parent-" + injectId,
        contractId,
        simulationId);
    trackDelete("DELETE FROM injects WHERE inject_id = ?", injectId);
    trackFirst("DELETE FROM injects_dependencies WHERE inject_children_id = ?", injectId);
    trackFirst("DELETE FROM injects_dependencies WHERE inject_parent_id = ?", injectId);
    seeded.add(new String[] {"injects", "inject_id", injectId, tenantId});
    return new SeededInject(simulationId, injectId);
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

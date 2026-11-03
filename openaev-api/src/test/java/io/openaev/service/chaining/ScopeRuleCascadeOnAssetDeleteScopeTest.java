package io.openaev.service.chaining;

import static io.openaev.config.TenantUriUtils.TENANT_BASE_PATH;
import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.AssetGroupFixture.createDefaultAssetGroupInput;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.rest.asset_group.form.AssetGroupBulkProcessingInput;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.UnexpectedRollbackException;

/**
 * Deleting an asset group removes the template scope rules that name it, in the deleter's
 * transaction, just before it commits. The request must commit for that listener to run, so the
 * class is not {@code @Transactional} and sweeps what it creates.
 *
 * <p>The failure cases inject a database error on the rule delete with a trigger, the only way to
 * make the real statement fail on the real stack.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=workflows,workflow_scope_rules")
@DisplayName("Deleting an asset group cascades to the template scope rules that name it")
class ScopeRuleCascadeOnAssetDeleteScopeTest extends IntegrationTest {

  private static final String TENANT_ASSET_GROUP_URI =
      TENANT_BASE_PATH + DEFAULT_TENANT_UUID + "/asset_groups";
  private static final String FAULT_TRIGGER = "scope_rule_cascade_test_fault";
  private static final String FAULT_SEQUENCE = "scope_rule_cascade_test_fault_calls";
  private static final String FAULT_MESSAGE = "scope rule cleanup refused by the test";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String exerciseId;
  private String workflowId;
  private String ruleId;
  private String assetGroupId;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    exerciseId = UUID.randomUUID().toString();
    workflowId = UUID.randomUUID().toString();
    ruleId = UUID.randomUUID().toString();
  }

  @AfterEach
  void sweep() {
    jdbc.execute("DROP TRIGGER IF EXISTS " + FAULT_TRIGGER + " ON workflow_scope_rules");
    jdbc.execute("DROP FUNCTION IF EXISTS " + FAULT_TRIGGER + "()");
    jdbc.execute("DROP SEQUENCE IF EXISTS " + FAULT_SEQUENCE);
    jdbc.update("DELETE FROM workflow_scope_rules WHERE workflow_scope_rule_id = ?", ruleId);
    jdbc.update("DELETE FROM workflows WHERE workflow_id = ?", workflowId);
    jdbc.update("DELETE FROM exercises WHERE exercise_id = ?", exerciseId);
    if (assetGroupId != null) {
      jdbc.update("DELETE FROM asset_groups WHERE asset_group_id = ?", assetGroupId);
    }
  }

  @Nested
  @DisplayName("When the cleanup succeeds")
  class CleanupSucceeds {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a template rule naming the group, deleting it on the tenant path drops both")
    void given_templateRuleNamingAssetGroup_should_deleteGroupAndRule() throws Exception {
      // -- Arrange --
      givenRuleNamingNewAssetGroup("TEMPLATE");

      // -- Act --
      int httpStatus = perform(delete(TENANT_ASSET_GROUP_URI + "/" + assetGroupId));

      // -- Assert --
      assertThat(httpStatus).as("delete status").isBetween(200, 299);
      assertThat(assetGroupRows()).as("asset group rows left").isZero();
      assertThat(ruleRows()).as("template rule rows left").isZero();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a template rule naming the group, deleting it with the X-Tenant-Ids header drops"
            + " both")
    void given_headerRoute_should_deleteGroupAndRule() throws Exception {
      // -- Arrange --
      givenRuleNamingNewAssetGroup("TEMPLATE");
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.hasCurrentTenant())
          .as("the header route must run with no ambient tenant, as it does in production")
          .isFalse();

      // -- Act --
      int httpStatus =
          perform(
              delete(ASSET_GROUP_URI + "/" + assetGroupId)
                  .header("X-Tenant-Ids", DEFAULT_TENANT_UUID));

      // -- Assert --
      assertThat(httpStatus).as("delete status").isBetween(200, 299);
      assertThat(assetGroupRows()).as("asset group rows left").isZero();
      assertThat(ruleRows()).as("template rule rows left").isZero();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given a run rule naming the group, deleting the group keeps the run rule")
    void given_runRuleNamingAssetGroup_should_keepRunRule() throws Exception {
      // -- Arrange --
      givenRuleNamingNewAssetGroup("RUN");

      // -- Act --
      int httpStatus = perform(delete(TENANT_ASSET_GROUP_URI + "/" + assetGroupId));

      // -- Assert --
      assertThat(httpStatus).as("delete status").isBetween(200, 299);
      assertThat(assetGroupRows()).as("asset group rows left").isZero();
      assertThat(ruleRows()).as("run rule rows left").isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("When the cleanup fails")
  class CleanupFails {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given a lock conflict on the first cleanup attempt, the bulk delete retries and drops"
            + " both")
    void given_transientLockConflictOnCleanup_should_retryBulkDeleteAndDropRule() throws Exception {
      // -- Arrange --
      givenRuleNamingNewAssetGroup("TEMPLATE");
      givenRuleDeleteFails("40P01", 1);
      AssetGroupBulkProcessingInput input = new AssetGroupBulkProcessingInput();
      input.setAssetGroupIdsToProcess(List.of(assetGroupId));

      // -- Act --
      int httpStatus =
          perform(
              delete(TENANT_ASSET_GROUP_URI)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON));

      // -- Assert --
      assertThat(httpStatus).as("bulk delete status").isBetween(200, 299);
      assertThat(jdbc.queryForObject("SELECT last_value FROM " + FAULT_SEQUENCE, Long.class))
          .as("cleanup attempts: the failed one, then the retry")
          .isEqualTo(2L);
      assertThat(assetGroupRows()).as("asset group rows left").isZero();
      assertThat(ruleRows()).as("template rule rows left").isZero();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName(
        "given the cleanup keeps failing, the delete fails on that cause and keeps the group and"
            + " the rule")
    void given_persistentCleanupFailure_should_failDeleteAndKeepGroupAndRule() throws Exception {
      // -- Arrange --
      givenRuleNamingNewAssetGroup("TEMPLATE");
      givenRuleDeleteFails("P0001", Integer.MAX_VALUE);

      // -- Act --
      Throwable failure = null;
      try {
        mvc.perform(delete(TENANT_ASSET_GROUP_URI + "/" + assetGroupId).with(csrf()));
      } catch (ServletException e) {
        failure = e;
      }

      // -- Assert --
      assertThat(failure).as("the delete must fail").isNotNull();
      assertThat(causeChain(failure))
          .as("the failure carries the cleanup's own cause, not an opaque rollback-only error")
          .noneMatch(UnexpectedRollbackException.class::isInstance)
          .anyMatch(t -> String.valueOf(t.getMessage()).contains(FAULT_MESSAGE));
      assertThat(assetGroupRows()).as("asset group rows left").isEqualTo(1);
      assertThat(ruleRows()).as("template rule rows left").isEqualTo(1);
    }
  }

  private void givenRuleNamingNewAssetGroup(String workflowStatus) throws Exception {
    String created =
        mvc.perform(
                post(TENANT_ASSET_GROUP_URI)
                    .content(asJsonString(createDefaultAssetGroupInput("cascade-probe")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assetGroupId = JsonPath.read(created, "$.asset_group_id");
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_mail_from, tenant_id)"
            + " VALUES (?, 'scoped simulation', 'sim@openaev.io', ?)",
        exerciseId,
        DEFAULT_TENANT_UUID);
    jdbc.update(
        "INSERT INTO workflows (workflow_id, workflow_status, workflow_simulation_id,"
            + " workflow_version, tenant_id) VALUES (?, ?::workflow_status, ?, 0, ?)",
        workflowId,
        workflowStatus,
        exerciseId,
        DEFAULT_TENANT_UUID);
    jdbc.update(
        "INSERT INTO workflow_scope_rules (workflow_scope_rule_id, workflow_id,"
            + " workflow_scope_rule_selected_mode, workflow_scope_rule_source,"
            + " workflow_scope_rule_value, workflow_scope_rule_value_type, tenant_id)"
            + " VALUES (?, ?, 'ALLOWLIST', 'ASSET_GROUP', ?, 'ASSET_GROUP_ID', ?)",
        ruleId,
        workflowId,
        assetGroupId,
        DEFAULT_TENANT_UUID);
  }

  /**
   * Makes the delete of this test's rule raise {@code sqlState} on its first {@code failures}
   * attempts. The call counter is a sequence because {@code nextval} survives the rollback of the
   * failed attempt.
   */
  private void givenRuleDeleteFails(String sqlState, int failures) {
    jdbc.execute("CREATE SEQUENCE " + FAULT_SEQUENCE);
    jdbc.execute(
        "CREATE FUNCTION "
            + FAULT_TRIGGER
            + "() RETURNS trigger AS $$ BEGIN IF nextval('"
            + FAULT_SEQUENCE
            + "') <= "
            + failures
            + " THEN RAISE EXCEPTION '"
            + FAULT_MESSAGE
            + "' USING ERRCODE = '"
            + sqlState
            + "'; END IF; RETURN OLD; END; $$ LANGUAGE plpgsql");
    jdbc.execute(
        "CREATE TRIGGER "
            + FAULT_TRIGGER
            + " BEFORE DELETE ON workflow_scope_rules FOR EACH ROW WHEN"
            + " (OLD.workflow_scope_rule_id = '"
            + ruleId
            + "') EXECUTE FUNCTION "
            + FAULT_TRIGGER
            + "()");
  }

  private static List<Throwable> causeChain(Throwable failure) {
    List<Throwable> chain = new ArrayList<>();
    for (Throwable t = failure; t != null && !chain.contains(t); t = t.getCause()) {
      chain.add(t);
    }
    return chain;
  }

  private int perform(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(csrf())).andReturn().getResponse().getStatus();
  }

  private Integer assetGroupRows() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM asset_groups WHERE asset_group_id = ?", Integer.class, assetGroupId);
  }

  private Integer ruleRows() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM workflow_scope_rules WHERE workflow_scope_rule_id = ?",
        Integer.class,
        ruleId);
  }
}

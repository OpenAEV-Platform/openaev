package io.openaev.service.attackpath.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Step;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Drives {@link AttackPathFindingIngestionService#copyFindings} the way the chaining engine does on
 * an execution update: a persisted inject with real findings on target endpoints, the execution
 * rows already frozen for those endpoints, the tenant carried from the event. The copy writes one
 * {@code attackpath_finding} row per (finding, endpoint) and one {@code
 * attackpath_execution_finding} link per (execution, finding), under the inject's tenant,
 * idempotently.
 *
 * <p>Ground truth is read through raw JDBC so a misplaced or missing row cannot hide behind a
 * scoped read. Deliberately not {@code @Transactional}: the copy commits in its own scoped
 * transaction, so the rows must outlive any test transaction (explicit cleanup instead).
 */
@TestPropertySource(
    properties = {"openaev.tenant.active-tables=attackpath_execution,attackpath_finding"})
@WithMockUser(isAdmin = true)
@DisplayName("attack path: copyFindings copies findings onto the snapshot per endpoint")
class AttackPathFindingIngestionServiceTest extends IntegrationTest {

  private static final String STEP_ID = "step-phaseb";

  @Autowired private AttackPathFindingIngestionService findingIngestionService;
  @Autowired private AttackPathExecutionIngestionService executionIngestionService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;

  private JdbcTemplate jdbc;
  private Tenant tenant;
  private String simulationId;
  private final List<String> userIds = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenant = tenantHelper.createTenantWithCurrentUser("ap-phaseb");
  }

  @AfterEach
  void cleanUp() {
    if (simulationId != null) {
      jdbc.update(
          "DELETE FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?",
          simulationId);
      jdbc.update(
          "DELETE FROM attackpath_finding WHERE attackpath_finding_simulation_id = ?",
          simulationId);
    }
    tenantHelper.deleteCommittedTenants(tenant.getId());
    // Users are not tenant-owned, so the tenant delete does not cascade to them.
    userIds.forEach(id -> jdbc.update("DELETE FROM users WHERE user_id = ?", id));
    userIds.clear();
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("a finding on the target endpoint is copied to one snapshot row and one exec link")
  void copiesFindingPerEndpointUnderTenant() {
    Endpoint endpoint = persistEndpoint();
    Inject inject = persistInjectWithCve("CVE-2026-1", List.of(endpoint));
    freezeExecution("exec-1", endpoint.getId());

    copy(inject);

    assertThat(findingCountForValue("CVE-2026-1", endpoint.getId())).isEqualTo(1);
    assertThat(linkCount("exec-1")).isEqualTo(1);
    // The row lands under the inject's tenant, not the default one.
    assertThat(findingCountUnderTenant(Tenant.DEFAULT_TENANT_UUID)).isZero();
  }

  @Test
  @DisplayName("re-running the copy on the same event writes no duplicate")
  void copyIsIdempotentOnReplay() {
    Endpoint endpoint = persistEndpoint();
    Inject inject = persistInjectWithCve("CVE-2026-2", List.of(endpoint));
    freezeExecution("exec-1", endpoint.getId());

    copy(inject);
    copy(inject);

    assertThat(findingCountForValue("CVE-2026-2", endpoint.getId())).isEqualTo(1);
    assertThat(linkCount("exec-1")).isEqualTo(1);
  }

  @Test
  @DisplayName("the same finding on two endpoints is two rows, one per endpoint")
  void sameValueOnTwoEndpointsMakesTwoRows() {
    Endpoint one = persistEndpoint();
    Endpoint two = persistEndpoint();
    Inject inject = persistInjectWithCve("CVE-2026-3", List.of(one, two));
    freezeExecution("exec-1", one.getId());
    freezeExecution("exec-2", two.getId());

    copy(inject);

    assertThat(findingCountForValue("CVE-2026-3", one.getId())).isEqualTo(1);
    assertThat(findingCountForValue("CVE-2026-3", two.getId())).isEqualTo(1);
    assertThat(distinctEndpointKeys("CVE-2026-3"))
        .containsExactlyInAnyOrder(one.getId(), two.getId());
  }

  @Test
  @DisplayName("the finding value is stored verbatim (a composite keeps its host:port form)")
  void valueIsStoredVerbatim() {
    Endpoint endpoint = persistEndpoint();
    Inject inject = persistInjectWithCve("10.0.0.5:445 (microsoft-ds)", List.of(endpoint));
    freezeExecution("exec-1", endpoint.getId());

    copy(inject);

    assertThat(findingCountForValue("10.0.0.5:445 (microsoft-ds)", endpoint.getId())).isEqualTo(1);
  }

  @Test
  @DisplayName("copied rows are cleared when the simulation is reset or deleted")
  void copiedRowsAreClearedOnSimulationDelete() {
    Endpoint endpoint = persistEndpoint();
    Inject inject = persistInjectWithCve("CVE-2026-4", List.of(endpoint));
    freezeExecution("exec-1", endpoint.getId());
    copy(inject);
    assertThat(findingCountForValue("CVE-2026-4", endpoint.getId())).isEqualTo(1);

    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            s -> executionIngestionService.deleteAllBySimulationId(simulationId, tenant.getId()));

    assertThat(findingCountForValue("CVE-2026-4", endpoint.getId())).isZero();
    assertThat(linkCount("exec-1")).isZero();
  }

  @Test
  @DisplayName(
      "a finding with no matching endpoint is skipped on a multi-endpoint step, not sprayed")
  void multiEndpointStepDoesNotSprayUnmatchedFindings() {
    // A finding with no asset, on a step that produced two distinct discovered endpoints.
    Inject inject = persistInjectWithCve("CVE-2026-5", List.of());
    freezeDiscoveredExecution("exec-1", "10.0.0.1");
    freezeDiscoveredExecution("exec-2", "10.0.0.2");

    copy(inject);

    assertThat(findingCountForValue("CVE-2026-5", "10.0.0.1")).isZero();
    assertThat(findingCountForValue("CVE-2026-5", "10.0.0.2")).isZero();
  }

  @Test
  @DisplayName(
      "a finding with no matching asset is attributed to the sole endpoint of a single-target step")
  void singleEndpointStepAttributesUnmatchedFindingToItsEndpoint() {
    Inject inject = persistInjectWithCve("CVE-2026-6", List.of());
    freezeDiscoveredExecution("exec-1", "10.0.0.9");

    copy(inject);

    assertThat(findingCountForValue("CVE-2026-6", "10.0.0.9")).isEqualTo(1);
    assertThat(linkCount("exec-1")).isEqualTo(1);
  }

  @Test
  @DisplayName("a finding naming a recipient lands on its PERSON row and on its team's TEAM row")
  void given_findingWithUser_should_attachToPersonRowAndItsTeamRow() {
    // -- ARRANGE -- a phishing step: one TEAM row and one PERSON row per recipient.
    Team team = persistTeam();
    User victim = persistUser();
    User other = persistUser();
    Inject inject =
        persistInjectWithCredentials("victim / hunter2", List.of(victim), List.of(team));
    freezeTeamExecution("exec-team", team.getId());
    freezePersonExecution("exec-victim", victim.getId(), team.getId());
    freezePersonExecution("exec-other", other.getId(), team.getId());

    // -- ACT --
    copy(inject);

    // -- ASSERT -- on the submitter, not on the other recipient.
    assertThat(findingCountForValue("victim / hunter2", victim.getId())).isEqualTo(1);
    assertThat(linkCount("exec-victim")).isEqualTo(1);
    assertThat(linkCount("exec-other")).isZero();
    // And on the submitter's team, through the action's own (injector -> team) execution, so the
    // team node, its panel and the action panel show what one of its members leaked.
    assertThat(findingCountForValue("victim / hunter2", team.getId())).isEqualTo(1);
    assertThat(linkCount("exec-team")).isEqualTo(1);
  }

  @Test
  @DisplayName("a finding naming only a team is attached to that team's TEAM row")
  void given_findingWithOnlyTeam_should_attachToTeamRow() {
    // -- ARRANGE --
    Team team = persistTeam();
    User recipient = persistUser();
    Inject inject = persistInjectWithCredentials("team-only", List.of(), List.of(team));
    freezeTeamExecution("exec-team", team.getId());
    freezePersonExecution("exec-recipient", recipient.getId(), team.getId());

    // -- ACT --
    copy(inject);

    // -- ASSERT --
    assertThat(findingCountForValue("team-only", team.getId())).isEqualTo(1);
    assertThat(linkCount("exec-team")).isEqualTo(1);
    assertThat(linkCount("exec-recipient")).isZero();
  }

  @Test
  @DisplayName("a finding's matching asset still wins over its users")
  void given_findingWithAssetAndUser_should_attachToAssetRowOnly() {
    // -- ARRANGE --
    Endpoint endpoint = persistEndpoint();
    User user = persistUser();
    Finding finding = FindingFixture.createDefaultFindingCredentials();
    finding.setValue("asset-first");
    finding.setAssets(new ArrayList<>(List.of(endpoint)));
    finding.setUsers(new ArrayList<>(List.of(user)));
    Inject inject = persistInjectWithFinding(finding);
    freezeExecution("exec-asset", endpoint.getId());
    freezePersonExecution("exec-person", user.getId(), "team-of-person");

    // -- ACT --
    copy(inject);

    // -- ASSERT --
    assertThat(findingCountForValue("asset-first", endpoint.getId())).isEqualTo(1);
    assertThat(linkCount("exec-asset")).isEqualTo(1);
    assertThat(linkCount("exec-person")).isZero();
  }

  @Test
  @DisplayName("a finding with no asset, user nor team is still skipped on a multi-target step")
  void given_findingWithoutAnyTarget_should_beSkippedOnMultiTargetStep() {
    // -- ARRANGE --
    Team team = persistTeam();
    User recipient = persistUser();
    Inject inject = persistInjectWithCredentials("orphan", List.of(), List.of());
    freezeTeamExecution("exec-team", team.getId());
    freezePersonExecution("exec-recipient", recipient.getId(), team.getId());

    // -- ACT --
    copy(inject);

    // -- ASSERT --
    assertThat(findingCountUnderTenant(tenant.getId())).isZero();
    assertThat(linkCount("exec-team")).isZero();
    assertThat(linkCount("exec-recipient")).isZero();
  }

  @Test
  @DisplayName("the deferred copy writes nothing until the caller's transaction commits")
  void given_deferredCopy_should_writeOnlyAfterCommit() {
    // -- ARRANGE --
    Team team = persistTeam();
    User victim = persistUser();
    Inject inject = persistInjectWithCredentials("deferred", List.of(victim), List.of(team));
    String findingId = inject.getFindings().getFirst().getId();
    freezeTeamExecution("exec-team", team.getId());
    freezePersonExecution("exec-victim", victim.getId(), team.getId());
    Step step = new Step();
    step.setId(STEP_ID);

    // -- ACT --
    TenantContext.setCurrentTenant(tenant.getId());
    Integer beforeCommit =
        new TransactionTemplate(transactionManager)
            .execute(
                s -> {
                  findingIngestionService.copyFindingAfterCommit(inject, step, findingId);
                  return findingCountForValue("deferred", victim.getId());
                });
    TenantContext.clearCurrentTenant();

    // -- ASSERT --
    assertThat(beforeCommit).isZero();
    assertThat(findingCountForValue("deferred", victim.getId())).isEqualTo(1);
    assertThat(linkCount("exec-victim")).isEqualTo(1);
  }

  @Test
  @DisplayName("the deferred copy writes only the given finding, not the inject's other findings")
  void given_deferredCopyOfOneFinding_should_writeOnlyThatFinding() {
    // -- ARRANGE -- two submissions on the same chained phishing step, by two recipients.
    Team team = persistTeam();
    User victim = persistUser();
    User other = persistUser();
    Finding submitted = FindingFixture.createDefaultFindingCredentials();
    submitted.setValue("victim / hunter2");
    submitted.setUsers(new ArrayList<>(List.of(victim)));
    Finding earlier = FindingFixture.createDefaultFindingCredentials();
    earlier.setValue("other / letmein");
    earlier.setUsers(new ArrayList<>(List.of(other)));
    Inject inject = persistInjectWithFindings(List.of(submitted, earlier));
    freezeTeamExecution("exec-team", team.getId());
    freezePersonExecution("exec-victim", victim.getId(), team.getId());
    freezePersonExecution("exec-other", other.getId(), team.getId());
    Step step = new Step();
    step.setId(STEP_ID);

    // -- ACT --
    TenantContext.setCurrentTenant(tenant.getId());
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            s -> findingIngestionService.copyFindingAfterCommit(inject, step, submitted.getId()));
    TenantContext.clearCurrentTenant();

    // -- ASSERT -- the given finding is on the snapshot, the inject's other one is not.
    assertThat(findingCountForValue("victim / hunter2", victim.getId())).isEqualTo(1);
    assertThat(linkCount("exec-victim")).isEqualTo(1);
    assertThat(findingCountForValue("other / letmein", other.getId())).isZero();
    assertThat(linkCount("exec-other")).isZero();
  }

  // -- helpers --

  private Endpoint persistEndpoint() {
    TenantContext.setCurrentTenant(tenant.getId());
    Endpoint endpoint =
        endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist().get();
    TenantContext.clearCurrentTenant();
    return endpoint;
  }

  // Persists exercise -> inject -> a CVE finding of the given value, linked to already-committed
  // endpoints, and returns the inject with its exercise and tenant set (as the seam hands it over).
  private Inject persistInjectWithCve(String value, List<Endpoint> endpoints) {
    Finding cve = FindingFixture.createDefaultCveFindingWithRandomTitle();
    cve.setValue(value);
    cve.setAssets(new ArrayList<>(endpoints));
    return persistInjectWithFinding(cve);
  }

  // A human-in-the-loop finding (phishing credentials): no asset, attributed to users and teams.
  private Inject persistInjectWithCredentials(String value, List<User> users, List<Team> teams) {
    Finding credentials = FindingFixture.createDefaultFindingCredentials();
    credentials.setValue(value);
    credentials.setUsers(new ArrayList<>(users));
    credentials.setTeams(new ArrayList<>(teams));
    return persistInjectWithFinding(credentials);
  }

  private Inject persistInjectWithFinding(Finding finding) {
    return persistInjectWithFindings(List.of(finding));
  }

  private Inject persistInjectWithFindings(List<Finding> findings) {
    TenantContext.setCurrentTenant(tenant.getId());
    InjectComposer.Composer injectWrapper =
        injectComposer.forInject(InjectFixture.getDefaultInject());
    findings.forEach(finding -> injectWrapper.withFinding(findingComposer.forFinding(finding)));
    ExerciseComposer.Composer exerciseWrapper =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withInject(injectWrapper);
    exerciseWrapper.persist();
    TenantContext.clearCurrentTenant();

    simulationId = exerciseWrapper.get().getId();
    Inject inject = injectWrapper.get();
    inject.setTenant(tenant);
    Exercise exercise = new Exercise();
    exercise.setId(simulationId);
    inject.setExercise(exercise);
    return inject;
  }

  private Team persistTeam() {
    TenantContext.setCurrentTenant(tenant.getId());
    Team team = teamComposer.forTeam(TeamFixture.createTeamWithName(null)).persist().get();
    TenantContext.clearCurrentTenant();
    return team;
  }

  private User persistUser() {
    User user = userComposer.forUser(UserFixture.getUserWithDefaultEmail()).persist().get();
    userIds.add(user.getId());
    return user;
  }

  private void freezeExecution(String executionId, String assetId) {
    jdbc.update(
        "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
            + " attackpath_execution_simulation_id, attackpath_execution_step_id,"
            + " attackpath_execution_source_kind, attackpath_execution_target_kind,"
            + " attackpath_execution_target_asset_id, attackpath_execution_target_key,"
            + " attackpath_execution_executed_at)"
            + " VALUES (?, ?, ?, ?, 'INJECTOR', 'ASSET', ?, ?, now())",
        executionId,
        tenant.getId(),
        simulationId,
        STEP_ID,
        assetId,
        assetId);
  }

  // A frozen discovered-target row: no asset id, the raw target lives in the endpoint key.
  private void freezeDiscoveredExecution(String executionId, String rawTarget) {
    jdbc.update(
        "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
            + " attackpath_execution_simulation_id, attackpath_execution_step_id,"
            + " attackpath_execution_source_kind, attackpath_execution_target_kind,"
            + " attackpath_execution_target_key, attackpath_execution_executed_at)"
            + " VALUES (?, ?, ?, ?, 'INJECTOR', 'DISCOVERED', ?, now())",
        executionId,
        tenant.getId(),
        simulationId,
        STEP_ID,
        rawTarget);
  }

  // The rows of a team-targeted step, as the ingestion freezes them: injector -> team, then
  // team -> person per recipient. No asset id: the team / user id is the target key.
  private void freezeTeamExecution(String executionId, String teamId) {
    freezeHumanTargetExecution(executionId, "INJECTOR", null, "TEAM", teamId);
  }

  private void freezePersonExecution(String executionId, String userId, String teamId) {
    freezeHumanTargetExecution(executionId, "TEAM", teamId, "PERSON", userId);
  }

  private void freezeHumanTargetExecution(
      String executionId,
      String sourceKind,
      String sourceAssetId,
      String targetKind,
      String targetKey) {
    jdbc.update(
        "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
            + " attackpath_execution_simulation_id, attackpath_execution_step_id,"
            + " attackpath_execution_source_kind, attackpath_execution_source_asset_id,"
            + " attackpath_execution_target_kind, attackpath_execution_target_key,"
            + " attackpath_execution_executed_at)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())",
        executionId,
        tenant.getId(),
        simulationId,
        STEP_ID,
        sourceKind,
        sourceAssetId,
        targetKind,
        targetKey);
  }

  private void copy(Inject inject) {
    Step step = new Step();
    step.setId(STEP_ID);
    TenantContext.setCurrentTenant(tenant.getId());
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(s -> findingIngestionService.copyFindings(inject, step));
    TenantContext.clearCurrentTenant();
  }

  private Integer findingCountForValue(String value, String endpointKey) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_finding"
            + " WHERE attackpath_finding_simulation_id = ? AND attackpath_finding_value = ?"
            + " AND attackpath_finding_endpoint_key = ? AND tenant_id = ?",
        Integer.class,
        simulationId,
        value,
        endpointKey,
        tenant.getId());
  }

  private Integer findingCountUnderTenant(String tenantId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_finding"
            + " WHERE attackpath_finding_simulation_id = ? AND tenant_id = ?",
        Integer.class,
        simulationId,
        tenantId);
  }

  private List<String> distinctEndpointKeys(String value) {
    return jdbc.queryForList(
        "SELECT DISTINCT attackpath_finding_endpoint_key FROM attackpath_finding"
            + " WHERE attackpath_finding_simulation_id = ? AND attackpath_finding_value = ?",
        String.class,
        simulationId,
        value);
  }

  private Integer linkCount(String executionId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM attackpath_execution_finding WHERE execution_id = ?",
        Integer.class,
        executionId);
  }
}

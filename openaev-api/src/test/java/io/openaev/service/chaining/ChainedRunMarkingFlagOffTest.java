package io.openaev.service.chaining;

import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.api.chaining.WorkflowConfigurationMapper;
import io.openaev.api.chaining.dto.WorkflowScopeRuleOutput;
import io.openaev.config.MarkedTables;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.service.AssetService;
import io.openaev.service.UserService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * With the {@code MARKING} preview feature off, the clearance work of #8555 is inert (D9): a user
 * that holds no clearance at all still reads the RED asset, nothing is dropped from the scope of a
 * workflow, and the system clearance a freeze runs at changes nothing a reader sees.
 *
 * <p>The suite runs with {@code MARKING} on (test configuration), so this class turns it off. The
 * asset is marked anyway, and the user sits in a group that grants no marking: with the feature on,
 * that user would not see the RED asset, which is what makes these assertions worth anything.
 *
 * <p>Deliberately NOT {@code @Transactional}: each step opens its own transaction, like the engine.
 */
@WithMockUser
@TestPropertySource(properties = "openaev.enabled-dev-features=")
@Import(ChainedRunMarkingFlagOffTest.RequestOf.class)
@DisplayName("A chained run with the MARKING feature off")
class ChainedRunMarkingFlagOffTest extends IntegrationTest {

  @Autowired private RequestOf requestOf;
  @Autowired private MarkedTables markedTables;
  @Autowired private ScopeSnapshotService scopeSnapshotService;
  @Autowired private WorkflowConfigurationMapper workflowConfigurationMapper;
  @Autowired private AssetService assetService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private WorkflowScopeRuleRepository workflowScopeRuleRepository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private DataSource dataSource;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private WorkflowComposer workflowComposer;

  private JdbcTemplate jdbc;
  private String tenantId;
  private Endpoint green;
  private Endpoint red;
  private User restricted;
  private String workflowId;

  @BeforeEach
  void seedARedAssetAndAUserWithNoClearance() throws Exception {
    // -- ARRANGE --
    assertThat(markedTables.tableNames())
        .as("the feature is off: no table is filtered, or these tests prove nothing")
        .isEmpty();
    jdbc = new JdbcTemplate(dataSource);
    tenantId = tenantHelper.createTenant("marking-off-" + UUID.randomUUID()).getId();
    String[] tlpRed = new String[1];
    inTenant(
        () -> {
          tlpRed[0] =
              markingDefinitionComposer
                  .forMarkingDefinition(
                      createMarkingDefinition(
                          MarkingDefinition.TYPE_TLP, uniqueDefinition(), 90, "#c62828"))
                  .withTenantId(tenantId)
                  .persist()
                  .get()
                  .getId();
          green = endpoint("green");
          red = endpoint("red");
          // A group that grants no marking: the user holds no clearance.
          restricted =
              userComposer
                  .forUser(
                      UserFixture.getUser(
                          "Restricted", "user", "restricted-" + UUID.randomUUID() + "@openaev.io"))
                  .withGroup(
                      tenantGroupComposer.forGroup(
                          TenantGroupFixture.getGroup("marking-off-" + UUID.randomUUID())))
                  .persist()
                  .get();
          return null;
        });
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        new String[] {tlpRed[0]},
        red.getId());
    clearanceCache.evictForUser(restricted.getId());
    workflowId = persistWorkflowAllowing(green, red);
  }

  @AfterEach
  void cleanup() {
    tenantHelper.deleteCommittedTenants(tenantId);
    jdbc.update("DELETE FROM users WHERE user_id = ?", restricted.getId());
    clearanceCache.evictForUser(restricted.getId());
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given a user with no clearance, should report no asset as hidden")
  void given_noClearance_should_reportNoAssetAsHidden() {
    // -- ACT --
    Set<String> hidden =
        inRequestOf(
            restricted,
            () ->
                scopeSnapshotService.hiddenAssetIds(tenantId, List.of(green.getId(), red.getId())));

    // -- ASSERT --
    assertThat(hidden).isEmpty();
  }

  @Test
  @DisplayName("given a user with no clearance, should return every rule of the configuration")
  void given_noClearance_should_returnEveryRule() {
    // -- ACT --
    List<String> returned =
        inRequestOf(
            restricted,
            () ->
                workflowConfigurationMapper
                    .toOutput(workflowRepository.findById(workflowId).orElseThrow())
                    .getWorkflowScopeRules()
                    .stream()
                    .map(WorkflowScopeRuleOutput::getRuleValue)
                    .toList());

    // -- ASSERT --
    assertThat(returned).containsExactlyInAnyOrder(green.getId(), red.getId());
  }

  @Test
  @DisplayName(
      "given a user with no clearance, should read the same assets at the system clearance")
  void given_noClearance_should_readTheSameAssetsAtTheSystemClearance() {
    // -- ARRANGE --
    List<String> ids = List.of(green.getId(), red.getId());
    TxCtx ctx = TxCtx.forTenant(tenantId);

    // -- ACT --
    List<Set<String>> read =
        inRequestOf(
            restricted,
            () ->
                List.of(
                    assetService.visibleAssetIds(ids),
                    tenantTx.withSystemClearance(ctx, () -> assetService.visibleAssetIds(ids))));

    // -- ASSERT --
    assertThat(read.get(0))
        .as("in the request of the user")
        .containsExactlyInAnyOrderElementsOf(ids);
    assertThat(read.get(1)).as("at the system clearance").containsExactlyInAnyOrderElementsOf(ids);
  }

  // -- Helpers --

  /** A request transaction: its scope comes from the {@link TxCtx} argument, as in the API. */
  public static class RequestOf {
    @Transactional(rollbackFor = Exception.class)
    public <T> T run(TxCtx ctx, Supplier<T> work) {
      return work.get();
    }
  }

  private <T> T inRequestOf(User user, Supplier<T> work) {
    Authentication previous = SecurityContextHolder.getContext().getAuthentication();
    SecurityContextHolder.getContext()
        .setAuthentication(UserService.buildAuthenticationToken(user));
    TenantContext.setCurrentTenant(tenantId);
    try {
      return requestOf.run(TxCtx.forTenant(tenantId), work);
    } finally {
      SecurityContextHolder.getContext().setAuthentication(previous);
      TenantContext.clearCurrentTenant();
    }
  }

  private <T> T inTenant(Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private Endpoint endpoint(String name) {
    return endpointComposer
        .forEndpoint(
            EndpointFixture.createEndpoint("marking-off-" + name + "-" + UUID.randomUUID()))
        .persist()
        .get();
  }

  private String persistWorkflowAllowing(Endpoint... assets) {
    return inTenant(
        () -> {
          Workflow workflow =
              workflowComposer
                  .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
                  .withSimulation(
                      exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
                  .persist()
                  .get();
          for (Endpoint asset : assets) {
            workflowScopeRuleRepository.save(
                WorkflowScopeRule.builder()
                    .tenant(new Tenant(tenantId))
                    .workflow(workflow)
                    .selectedMode(ScopeRuleSelectedMode.ALLOWLIST)
                    .ruleSource(ScopeRuleSource.ASSET)
                    .valueType(ScopeRuleValueType.ASSET_ID)
                    .ruleValue(asset.getId())
                    .build());
          }
          return workflow.getId();
        });
  }
}

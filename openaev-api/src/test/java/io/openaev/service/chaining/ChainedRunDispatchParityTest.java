package io.openaev.service.chaining;

import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import io.openaev.IntegrationTest;
import io.openaev.api.chaining.InjectExecutionStep;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.ScopeRuleSelectedMode;
import io.openaev.database.model.ScopeRuleSource;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowScopeRule;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.repository.WorkflowRepository;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.service.AssetToExecute;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.inject.service.ResolvedAssetsToExecute;
import io.openaev.service.FaithfulCopyTransaction;
import io.openaev.service.ScenarioToExerciseService;
import io.openaev.service.UserService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.StepFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.WorkflowFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.fixtures.composers.WorkflowComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A chained run executes on a marked asset under its <b>launcher's</b> clearance (#8555, D7).
 *
 * <p>The scope is expanded into one inject per asset by the engine, in the background, at the
 * system clearance: it sees every asset of the allow list, a RED one included, and applies the deny
 * list. Each inject is then dispatched through {@link
 * InjectService#resolveAssetsToExecuteByClearance}, the same gate as a classic simulation, which
 * splits its targets by the clearance of the user who launched the simulation. The two steps are
 * chained here on the real components, for a GREEN launcher and a RED control, with a RED asset
 * first allowed and then denied.
 *
 * <p>The first evaluation at launch does not run under one clearance, it depends on the entry
 * point: the status change of a simulation and the creation of an autonomous run read the scope in
 * the request of the launcher, under the launcher's own clearance, while the launch of a scenario
 * runs the chained start inside {@code FaithfulCopyTransaction}, at the system clearance. {@link
 * FirstEvaluationAtLaunch} pins both, so the RED asset gets no inject and no trace in the first
 * case, and an inject dropped at the gate in the second. The scheduled start of a simulation runs
 * in a job, at the system clearance, like a later step.
 *
 * <p>Not covered: the {@code ASSET_CLEARANCE_RESTRICTED} trace itself, written by the executor from
 * the assets this gate drops, and the endpoints themselves (the request is reproduced by a {@code
 * TxCtx} transaction and the launcher's authentication, not by a MockMvc call).
 *
 * <p>Deliberately NOT {@code @Transactional}: each step opens its own transaction, like the engine.
 * {@code MARKING} is on (test configuration).
 */
@WithMockUser
@Import(ChainedRunDispatchParityTest.RequestOf.class)
@DisplayName("A chained run dispatches under the clearance of its launcher")
class ChainedRunDispatchParityTest extends IntegrationTest {

  @Autowired private RequestOf requestOf;
  @Autowired private ScenarioToExerciseService scenarioToExerciseService;
  @Autowired private FaithfulCopyTransaction faithfulCopy;
  @Autowired private UserRepository userRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ExerciseService exerciseService;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectExecutionStep injectExecutionStep;
  @Autowired private InjectService injectService;
  @Autowired private WorkflowRepository workflowRepository;
  @Autowired private WorkflowScopeRuleRepository workflowScopeRuleRepository;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private DataSource dataSource;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private WorkflowComposer workflowComposer;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String tlpGreen;
  private String tlpRed;
  private Endpoint green;
  private Endpoint red;
  private User greenLauncher;
  private User redLauncher;
  private String workflowRedAllowed;
  private String workflowRedDenied;

  /** What a run of the scope does, for one launcher: the targets and the gate's split of them. */
  private record Outcome(Set<String> expanded, Set<String> cleared, Set<String> restricted) {}

  @BeforeEach
  void seedAScopeWithAGreenAndARedAsset() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = tenantHelper.createTenant("dispatch-parity-" + UUID.randomUUID()).getId();
    String[] groupIds = new String[2];
    inTenant(
        () -> {
          // Orders above the seeded 10..50 band so a fixture never ties with a default.
          tlpGreen = marking(80);
          tlpRed = marking(90);
          green = endpoint("green");
          red = endpoint("red");
          TenantGroupComposer.Composer greenGroup = group("green");
          TenantGroupComposer.Composer redGroup = group("red");
          greenLauncher = launcher("green", greenGroup);
          redLauncher = launcher("red", redGroup);
          groupIds[0] = greenGroup.get().getId();
          groupIds[1] = redGroup.get().getId();
          return null;
        });
    String greenGroupId = groupIds[0];
    String redGroupId = groupIds[1];
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)", greenGroupId, tlpGreen);
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)", redGroupId, tlpGreen);
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)", redGroupId, tlpRed);
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        new String[] {tlpGreen},
        green.getId());
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?", new String[] {tlpRed}, red.getId());
    clearanceCache.evictForUser(greenLauncher.getId());
    clearanceCache.evictForUser(redLauncher.getId());

    workflowRedAllowed = persistWorkflow(false);
    workflowRedDenied = persistWorkflow(true);
  }

  @AfterEach
  void cleanup() {
    tenantHelper.deleteCommittedTenants(tenantId);
    for (User launcher : List.of(greenLauncher, redLauncher)) {
      jdbc.update("DELETE FROM users WHERE user_id = ?", launcher.getId());
      clearanceCache.evictForUser(launcher.getId());
    }
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("the expansion of the scope, at the system clearance")
  class Expansion {

    @Test
    @DisplayName("given a RED asset on the allow list, should expand one target for it")
    void given_redAssetAllowed_should_expandATargetForIt() {
      // -- ACT --
      Set<String> expanded = expand(workflowRedAllowed);

      // -- ASSERT --
      assertThat(expanded)
          .as("the engine sees the RED asset, whoever launched the run")
          .containsExactlyInAnyOrder(green.getId(), red.getId());
    }

    @Test
    @DisplayName("given a RED asset on the deny list, should not expand it")
    void given_redAssetDenied_should_notExpandIt() {
      // -- ACT --
      Set<String> expanded = expand(workflowRedDenied);

      // -- ASSERT --
      assertThat(expanded).containsExactly(green.getId());
    }
  }

  @Nested
  @DisplayName("a RED asset on the allow list, not denied")
  class RedAssetAllowed {

    @Test
    @DisplayName("given a GREEN launcher, should drop the RED asset at the dispatch gate")
    void given_greenLauncher_should_dropTheRedAsset() {
      // -- ACT --
      Outcome outcome = run(workflowRedAllowed, greenLauncher);

      // -- ASSERT --
      assertThat(outcome.expanded()).containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(outcome.cleared()).containsExactly(green.getId());
      assertThat(outcome.restricted())
          .as("the RED asset gets its inject, dropped and traced as not targeted")
          .containsExactly(red.getId());
    }

    @Test
    @DisplayName("given a RED launcher (control), should execute on both assets")
    void given_redLauncher_should_executeOnBothAssets() {
      // -- ACT --
      Outcome outcome = run(workflowRedAllowed, redLauncher);

      // -- ASSERT --
      assertThat(outcome.cleared()).containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(outcome.restricted()).isEmpty();
    }
  }

  @Nested
  @DisplayName("a RED asset on the deny list")
  class RedAssetDenied {

    @Test
    @DisplayName("given a GREEN launcher, should execute on the GREEN asset only, nothing dropped")
    void given_greenLauncher_should_executeOnTheGreenAssetOnly() {
      // -- ACT --
      Outcome outcome = run(workflowRedDenied, greenLauncher);

      // -- ASSERT --
      assertThat(outcome.cleared()).containsExactly(green.getId());
      assertThat(outcome.restricted())
          .as("the list stops the RED asset before the gate, so nothing is traced")
          .isEmpty();
    }

    @Test
    @DisplayName("given a RED launcher (control), should execute on the same asset")
    void given_redLauncher_should_executeOnTheSameAsset() {
      // -- ACT --
      Outcome outcome = run(workflowRedDenied, redLauncher);

      // -- ASSERT --
      assertThat(outcome.cleared())
          .as("the deny list gives the same result for a RED and a GREEN launcher")
          .containsExactly(green.getId());
      assertThat(outcome.restricted()).isEmpty();
    }
  }

  @Nested
  @DisplayName("the first evaluation at launch, in the request of the launcher")
  class FirstEvaluationAtLaunch {

    @Test
    @DisplayName(
        "given a status change or an autonomous run launched by a GREEN user, should not expand"
            + " the RED asset: no inject, no trace")
    void given_greenLauncherRequest_should_notExpandTheRedAsset() {
      // -- ACT --
      Set<String> expanded = expandInRequestOf(greenLauncher, workflowRedAllowed, false);
      Outcome outcome = dispatch(expanded, greenLauncher);

      // -- ASSERT --
      assertThat(expanded)
          .as("the scope is read under the launcher's own clearance, which does not cover RED")
          .containsExactly(green.getId());
      assertThat(outcome.cleared()).containsExactly(green.getId());
      assertThat(outcome.restricted())
          .as("the RED asset has no inject, so nothing is dropped and no trace is written")
          .isEmpty();
    }

    @Test
    @DisplayName("given the same request made by a RED user (control), should expand both assets")
    void given_redLauncherRequest_should_expandBothAssets() {
      // -- ACT --
      Set<String> expanded = expandInRequestOf(redLauncher, workflowRedAllowed, false);
      Outcome outcome = dispatch(expanded, redLauncher);

      // -- ASSERT --
      assertThat(expanded).containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(outcome.cleared()).containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(outcome.restricted()).isEmpty();
    }

    @Test
    @DisplayName(
        "given a scenario launch by a GREEN user (faithful copy, system clearance), should expand"
            + " the RED asset and drop it at the gate, like a later step")
    void given_scenarioLaunchByAGreenUser_should_behaveLikeALaterStep() {
      // -- ACT --
      Set<String> expanded = expandInRequestOf(greenLauncher, workflowRedAllowed, true);
      Outcome outcome = dispatch(expanded, greenLauncher);

      // -- ASSERT --
      assertThat(expanded)
          .as("the copy of a scenario launch runs at the system clearance")
          .containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(outcome.cleared()).containsExactly(green.getId());
      assertThat(outcome.restricted()).containsExactly(red.getId());
    }
  }

  /**
   * The three autonomous entry points (create, restart, promote to a real run) copy a scenario into
   * a simulation, in the request of the operator, through the faithful copy like the launch of a
   * scenario, and stamp the operator as its launcher ({@code AutonomousRunServiceTest} pins both).
   * The simulation is then dispatched through the same gate as any other, so the operator's
   * clearance decides what runs. Without the faithful copy, the copy would run under the operator's
   * clearance and drop the targets it cannot see.
   */
  @Nested
  @DisplayName("the simulation an autonomous entry point creates from a scenario")
  class AutonomousEntryPoints {

    private String scenarioId;

    @BeforeEach
    void seedAScenarioTargetingBothAssets() {
      scenarioId = persistScenarioTargetingBothAssets();
    }

    @Test
    @DisplayName("given a GREEN operator, should keep the RED target and drop it at the gate")
    void given_greenOperator_should_keepTheRedTargetAndDropItAtTheGate() {
      // -- ACT --
      Outcome outcome = copyThenDispatch(scenarioId, greenLauncher);

      // -- ASSERT --
      assertThat(outcome.expanded())
          .as("the copy keeps the targets the operator is not cleared for, like a scenario launch")
          .containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(outcome.cleared()).containsExactly(green.getId());
      assertThat(outcome.restricted()).containsExactly(red.getId());
    }

    @Test
    @DisplayName("given a RED operator (control), should execute on both assets")
    void given_redOperator_should_executeOnBothAssets() {
      // -- ACT --
      Outcome outcome = copyThenDispatch(scenarioId, redLauncher);

      // -- ASSERT --
      assertThat(outcome.expanded()).containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(outcome.cleared()).containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(outcome.restricted()).isEmpty();
    }
  }

  /**
   * A reset keeps the scope of the simulation whole, as the reset of a classic simulation keeps its
   * targets: only the clearance of the launcher narrows what is seen and launched. The cleanup of
   * ghost rules must therefore read the assets at the system clearance, or a rule on an asset the
   * user is not cleared for reads as "deleted" and is removed.
   */
  @Nested
  @DisplayName("the reset of a chained simulation")
  class Reset {

    private String simulationId;
    private String templateWorkflowId;

    @BeforeEach
    void seedACanceledSimulationScopedOnBothAssets() {
      inTenant(
          () -> {
            Exercise simulation = ExerciseFixture.createDefaultExercise();
            simulation.setStatus(ExerciseStatus.CANCELED);
            Workflow template =
                workflowComposer
                    .forWorkflow(WorkflowFixture.getDefaultWorkflowTemplate())
                    .withSimulation(exerciseComposer.forExercise(simulation))
                    .persist()
                    .get();
            rule(template, ScopeRuleSelectedMode.ALLOWLIST, green);
            rule(template, ScopeRuleSelectedMode.ALLOWLIST, red);
            simulationId = template.getSimulation().getId();
            templateWorkflowId = template.getId();
            return null;
          });
    }

    @Test
    @DisplayName("given a GREEN user, should keep the rule of the RED asset in the scope")
    void given_greenUser_should_keepTheRuleOfTheRedAsset() {
      // -- ACT --
      reset(greenLauncher);

      // -- ASSERT --
      assertThat(scopeAssetIds())
          .as("the scope tab of the simulation keeps both assets after the reset")
          .containsExactlyInAnyOrder(green.getId(), red.getId());
    }

    @Test
    @DisplayName("given a RED user (control), should keep both rules")
    void given_redUser_should_keepBothRules() {
      // -- ACT --
      reset(redLauncher);

      // -- ASSERT --
      assertThat(scopeAssetIds()).containsExactlyInAnyOrder(green.getId(), red.getId());
    }

    @Test
    @DisplayName("given an asset deleted for real, should still drop its ghost rule")
    void given_deletedAsset_should_stillDropItsGhostRule() {
      // -- ARRANGE --
      jdbc.update("DELETE FROM assets WHERE asset_id = ?", red.getId());

      // -- ACT --
      reset(greenLauncher);

      // -- ASSERT --
      assertThat(scopeAssetIds()).containsExactly(green.getId());
    }

    private void reset(User user) {
      inRequestOf(
          user,
          () -> {
            try {
              return exerciseService.changeExerciseStatus(ExerciseStatus.SCHEDULED, simulationId);
            } catch (ChainingException e) {
              throw new IllegalStateException(e);
            }
          });
    }

    private Set<String> scopeAssetIds() {
      return inTenant(
          () ->
              workflowRepository
                  .findById(templateWorkflowId)
                  .orElseThrow()
                  .getWorkflowScopeRules()
                  .stream()
                  .map(WorkflowScopeRule::getRuleValue)
                  .collect(Collectors.toSet()));
    }
  }

  // -- Engine steps --

  /**
   * The targets the engine builds for the scope: what the queue consumer does, in a transaction at
   * the system clearance. A default step template is asset-centric (no contract to classify it).
   */
  private Set<String> expand(String workflowId) {
    return inTenant(() -> expandInCurrentTransaction(workflowId));
  }

  private Set<String> expandInCurrentTransaction(String workflowId) {
    Workflow workflow = workflowRepository.findById(workflowId).orElseThrow();
    return injectExecutionStep
        .expandTargetBatches(
            List.of(new ConditionService.ExecutionBatch("{}", List.of(), null)),
            workflow,
            StepFixture.getDefaultStepTemplate())
        .stream()
        .map(
            batch ->
                JsonParser.parseString(batch.inputString())
                    .getAsJsonObject()
                    .getAsJsonObject("_target")
                    .get("assetId")
                    .getAsString())
        .collect(Collectors.toSet());
  }

  /**
   * The same expansion, in the transaction of an HTTP request made by {@code launcher}: the aspect
   * reads the {@link TxCtx} and sets the launcher's own clearance, as it does for the status change
   * of a simulation or the creation of an autonomous run. With {@code widenToSystemClearance}, the
   * expansion runs inside {@link TenantScopedTransaction#withSystemClearance}, which is what {@code
   * FaithfulCopyTransaction} does around the chained start of a scenario launch.
   */
  private Set<String> expandInRequestOf(
      User launcher, String workflowId, boolean widenToSystemClearance) {
    TxCtx ctx = TxCtx.forTenant(tenantId);
    return inRequestOf(
        launcher,
        () ->
            widenToSystemClearance
                ? tenantTx.withSystemClearance(ctx, () -> expandInCurrentTransaction(workflowId))
                : expandInCurrentTransaction(workflowId));
  }

  /**
   * Runs {@code work} in the transaction of an HTTP request made by {@code launcher}: the aspect
   * reads the {@link TxCtx} and sets the tenant scope and the launcher's own clearance.
   */
  private <T> T inRequestOf(User launcher, Supplier<T> work) {
    Authentication previous = SecurityContextHolder.getContext().getAuthentication();
    SecurityContextHolder.getContext()
        .setAuthentication(UserService.buildAuthenticationToken(launcher));
    TenantContext.setCurrentTenant(tenantId);
    try {
      return requestOf.run(TxCtx.forTenant(tenantId), work);
    } finally {
      SecurityContextHolder.getContext().setAuthentication(previous);
      TenantContext.clearCurrentTenant();
    }
  }

  /**
   * What an autonomous entry point does with a scenario: copies it into a running simulation with
   * {@link ScenarioToExerciseService#toExercise}, through the faithful copy, in the request of the
   * operator, who becomes the launcher. Then dispatches every inject of the simulation through the
   * gate. The targets are the assets the copied injects hold, read at the system clearance so that
   * none is hidden from the check.
   */
  private Outcome copyThenDispatch(String scenarioId, User operator) {
    String simulationId =
        inRequestOf(
            operator,
            () ->
                faithfulCopy.execute(
                    TxCtx.forTenant(tenantId),
                    () ->
                        scenarioToExerciseService
                            .toExercise(
                                scenarioRepository.findById(scenarioId).orElseThrow(),
                                Instant.now().plusSeconds(60),
                                true,
                                userRepository.findById(operator.getId()).orElseThrow())
                            .getId()));
    return inTenant(
        () -> {
          Exercise simulation = exerciseRepository.findById(simulationId).orElseThrow();
          Set<String> targets = new HashSet<>();
          Set<String> cleared = new HashSet<>();
          Set<String> restricted = new HashSet<>();
          for (Inject inject : simulation.getInjects()) {
            inject.getAssets().forEach(asset -> targets.add(asset.getId()));
            ResolvedAssetsToExecute resolved =
                injectService.resolveAssetsToExecuteByClearance(inject);
            resolved.cleared().stream()
                .map(AssetToExecute::asset)
                .map(Asset::getId)
                .forEach(cleared::add);
            resolved.withoutClearance().stream().map(Asset::getId).forEach(restricted::add);
          }
          return new Outcome(targets, cleared, restricted);
        });
  }

  private String persistScenarioTargetingBothAssets() {
    return inTenant(
        () -> {
          Inject inject = InjectFixture.getInjectWithoutContract();
          // Loaded again: the assets were marked straight in the database after they were saved.
          inject.setAssets(
              new ArrayList<>(
                  List.of(
                      entityManager.find(Endpoint.class, green.getId()),
                      entityManager.find(Endpoint.class, red.getId()))));
          InjectComposer.Composer composed =
              injectComposer
                  .forInject(inject)
                  .withInjectorContract(
                      injectorContractComposer
                          .forInjectorContract(
                              InjectorContractFixture.createDefaultInjectorContract())
                          .withInjector(
                              InjectorFixture.createDefaultInjector(
                                  "dispatch-parity-" + UUID.randomUUID())));
          Scenario scenario = ScenarioFixture.getScenario();
          scenario.setTenant(new Tenant(tenantId));
          return scenarioComposer
              .forScenario(scenario)
              .withInject(composed)
              .persist()
              .get()
              .getId();
        });
  }

  /** A request transaction: its scope comes from the {@link TxCtx} argument, as in the API. */
  public static class RequestOf {
    @Transactional(rollbackFor = Exception.class)
    public <T> T run(TxCtx ctx, Supplier<T> work) {
      return work.get();
    }
  }

  /**
   * Expands the scope, then dispatches one inject per target of a simulation launched by {@code
   * launcher}, as a chained step does: each inject holds its single target asset.
   */
  private Outcome run(String workflowId, User launcher) {
    return dispatch(expand(workflowId), launcher);
  }

  private Outcome dispatch(Set<String> expanded, User launcher) {
    return inTenant(
        () -> {
          Exercise simulation = ExerciseFixture.createDefaultExercise();
          simulation.setLaunchedBy(launcher);
          ExerciseComposer.Composer simulationComposed = exerciseComposer.forExercise(simulation);
          List<InjectComposer.Composer> injects = new ArrayList<>();
          for (String assetId : expanded) {
            Inject inject = InjectFixture.getInjectWithoutContract();
            // Loaded again: the assets were marked straight in the database after they were saved.
            inject.setAssets(new ArrayList<>(List.of(entityManager.find(Endpoint.class, assetId))));
            InjectComposer.Composer composed =
                injectComposer
                    .forInject(inject)
                    .withInjectorContract(
                        injectorContractComposer
                            .forInjectorContract(
                                InjectorContractFixture.createDefaultInjectorContract())
                            .withInjector(
                                InjectorFixture.createDefaultInjector(
                                    "dispatch-parity-" + UUID.randomUUID())));
            simulationComposed.withInject(composed);
            injects.add(composed);
          }
          simulationComposed.persist();
          entityManager.flush();

          Set<String> cleared = new java.util.HashSet<>();
          Set<String> restricted = new java.util.HashSet<>();
          for (InjectComposer.Composer composed : injects) {
            ResolvedAssetsToExecute resolved =
                injectService.resolveAssetsToExecuteByClearance(composed.get());
            resolved.cleared().stream()
                .map(AssetToExecute::asset)
                .map(Asset::getId)
                .forEach(cleared::add);
            resolved.withoutClearance().stream().map(Asset::getId).forEach(restricted::add);
          }
          return new Outcome(expanded, cleared, restricted);
        });
  }

  // -- Helpers --

  private String persistWorkflow(boolean denyRed) {
    return inTenant(
        () -> {
          Workflow workflow =
              workflowComposer
                  .forWorkflow(WorkflowFixture.getDefaultWorkflowExecution(WorkflowStatus.RUN))
                  .withSimulation(
                      exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()))
                  .persist()
                  .get();
          rule(workflow, ScopeRuleSelectedMode.ALLOWLIST, green);
          rule(workflow, ScopeRuleSelectedMode.ALLOWLIST, red);
          if (denyRed) {
            rule(workflow, ScopeRuleSelectedMode.DENYLIST, red);
          }
          return workflow.getId();
        });
  }

  private void rule(Workflow workflow, ScopeRuleSelectedMode mode, Endpoint asset) {
    workflowScopeRuleRepository.save(
        WorkflowScopeRule.builder()
            .tenant(new Tenant(tenantId))
            .workflow(workflow)
            .selectedMode(mode)
            .ruleSource(ScopeRuleSource.ASSET)
            .valueType(ScopeRuleValueType.ASSET_ID)
            .ruleValue(asset.getId())
            .build());
  }

  private String marking(int order) {
    return markingDefinitionComposer
        .forMarkingDefinition(
            createMarkingDefinition(
                MarkingDefinition.TYPE_TLP, uniqueDefinition(), order, "#c62828"))
        .withTenantId(tenantId)
        .persist()
        .get()
        .getId();
  }

  private Endpoint endpoint(String name) {
    return endpointComposer
        .forEndpoint(
            EndpointFixture.createEndpoint("dispatch-parity-" + name + "-" + UUID.randomUUID()))
        .persist()
        .get();
  }

  private TenantGroupComposer.Composer group(String name) {
    return tenantGroupComposer.forGroup(
        TenantGroupFixture.getGroup("dispatch-parity-" + name + "-" + UUID.randomUUID()));
  }

  private User launcher(String name, TenantGroupComposer.Composer group) {
    return userComposer
        .forUser(
            UserFixture.getUser(
                "Launcher", name, "launcher-" + name + "-" + UUID.randomUUID() + "@openaev.io"))
        .withGroup(group)
        .persist()
        .get();
  }

  private <T> T inTenant(Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}

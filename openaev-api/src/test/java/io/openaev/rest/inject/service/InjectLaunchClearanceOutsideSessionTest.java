package io.openaev.rest.inject.service;

import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.User;
import io.openaev.database.repository.InjectRepository;
import io.openaev.execution.ExecutableInject;
import io.openaev.helper.InjectHelper;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The dispatch of a scheduled inject resolves its launcher's clearance on an inject loaded by an
 * earlier transaction (#8730): {@link InjectHelper#getInjectsToRun()} loads it in one {@code
 * TenantScopedTransaction}, the job runs it in another. The simulation and its launcher are lazy,
 * so when the launcher is not the inject's author (an imported scenario, launched by someone else),
 * nothing has loaded them: reading them through the inject threw "Could not initialize proxy ... no
 * session" and the inject ended in ERROR.
 *
 * <p>This reproduces that boundary rather than calling {@code getInjectsToRun()}, which takes every
 * due inject of the database. Deliberately NOT {@code @Transactional}: each step opens its own
 * transaction, like the job. {@code MARKING} is on (test configuration), and the launcher holds
 * {@code TLP:GREEN} only, so its clearance must still be applied: GREEN runs, RED does not.
 */
@WithMockUser
@DisplayName("Dispatch resolves the launcher of an inject loaded by an earlier transaction")
class InjectLaunchClearanceOutsideSessionTest extends IntegrationTest {

  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectService injectService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private DataSource dataSource;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String launcherId;
  private String groupId;
  private String tlpGreen;
  private String tlpRed;
  private Endpoint green;
  private Endpoint red;
  private String injectId;

  @BeforeEach
  void seedASimulationLaunchedByAGreenUser() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = tenantHelper.createTenant("launch-clearance-" + UUID.randomUUID()).getId();
    inTenant(
        () -> {
          // Orders above the seeded 10..50 band so a fixture never ties with a default.
          tlpGreen = marking(60);
          tlpRed = marking(70);
          green = endpoint("green");
          red = endpoint("red");
          TenantGroupComposer.Composer group =
              tenantGroupComposer.forGroup(
                  TenantGroupFixture.getGroup("launch-clearance-" + UUID.randomUUID()));
          User launcher =
              userComposer
                  .forUser(
                      UserFixture.getUser(
                          "Launcher", "Green", "launcher-" + UUID.randomUUID() + "@openaev.io"))
                  .withGroup(group)
                  .persist()
                  .get();
          launcherId = launcher.getId();
          groupId = group.get().getId();

          // An imported scenario's inject has no author: the launcher is never loaded with it.
          Inject inject = InjectFixture.getInjectWithoutContract();
          inject.setAssets(new ArrayList<>(List.of(green, red)));
          InjectComposer.Composer injectComposed =
              injectComposer
                  .forInject(inject)
                  .withInjectorContract(
                      injectorContractComposer
                          .forInjectorContract(
                              InjectorContractFixture.createDefaultInjectorContract())
                          .withInjector(
                              InjectorFixture.createDefaultInjector(
                                  "launch-clearance-" + UUID.randomUUID())));
          Exercise simulation = ExerciseFixture.createDefaultExercise();
          simulation.setLaunchedBy(launcher);
          exerciseComposer.forExercise(simulation).withInject(injectComposed).persist();
          injectId = injectComposed.get().getId();
          return null;
        });
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)", groupId, tlpGreen);
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        new String[] {tlpGreen},
        green.getId());
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?", new String[] {tlpRed}, red.getId());
    clearanceCache.evictForUser(launcherId);
  }

  @AfterEach
  void cleanup() {
    tenantHelper.deleteCommittedTenants(tenantId);
    jdbc.update("DELETE FROM users WHERE user_id = ?", launcherId);
    clearanceCache.evictForUser(launcherId);
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName(
      "given an inject loaded by a closed transaction, should resolve its expected targets")
  void given_injectLoadedByAClosedTransaction_should_resolveTheLauncherClearance() {
    // -- ARRANGE --
    Inject loadedEarlier = loadAsTheJobDoes();

    // -- ACT --
    List<AssetToExecute> resolved =
        inTenant(() -> injectService.resolveAllAssetsToExecute(loadedEarlier));

    // -- ASSERT --
    assertThat(resolved)
        .extracting(target -> target.asset().getId())
        .containsExactly(green.getId());
  }

  @Test
  @DisplayName(
      "given an inject loaded by a closed transaction, should dispatch to its GREEN target")
  void given_injectLoadedByAClosedTransaction_should_dispatchUnderTheLauncherClearance() {
    // -- ARRANGE --
    Inject loadedEarlier = loadAsTheJobDoes();

    // -- ACT --
    AgentsAndAssetsAgentless dispatched =
        inTenant(() -> injectService.getAgentsAndAgentlessAssetsByInject(loadedEarlier));

    // -- ASSERT --
    assertThat(dispatched.assetsAgentless())
        .extracting(Asset::getId)
        .containsExactly(green.getId());
  }

  // -- Helpers --

  /**
   * Loads the inject in its own transaction and lets it close, initialising what {@link
   * InjectHelper} reads into the {@link ExecutableInject} (targets, contract), and nothing else:
   * the simulation and its launcher stay uninitialised, as in the job.
   */
  private Inject loadAsTheJobDoes() {
    return inTenant(
        () -> {
          Inject inject = injectRepository.findById(injectId).orElseThrow();
          Hibernate.initialize(inject.getAssets());
          Hibernate.initialize(inject.getAssetGroups());
          inject.getInjectorContract().ifPresent(Hibernate::initialize);
          return inject;
        });
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
            EndpointFixture.createEndpoint("launch-clearance-" + name + "-" + UUID.randomUUID()))
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

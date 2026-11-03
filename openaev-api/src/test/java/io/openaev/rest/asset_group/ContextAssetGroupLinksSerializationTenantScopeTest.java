package io.openaev.rest.asset_group;

import static io.openaev.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.AssetGroupFixture.createDefaultAssetGroup;
import static io.openaev.utils.fixtures.EndpointFixture.createEndpoint;
import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultExercise;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.AssetGroupRepository;
import io.openaev.database.repository.AssetRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset_group.form.AssetGroupInput;
import io.openaev.rest.asset_group.form.UpdateAssetsOnAssetGroupInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * With {@code assets} and {@code tags} on v2 tenant isolation, {@code GET
 * /exercises/{id}/asset-groups} and {@code GET /scenarios/{id}/asset-groups} return raw {@code
 * AssetGroup} entities and would serialize their lazy {@code asset_group_assets} and {@code
 * asset_group_tags} open-in-view, AFTER the controller transaction has committed and {@code
 * app.current_tenants} is cleared: the lookup then fails closed to an empty array. The simulation
 * validation page puts these asset groups in the store, and the inject form reuses them for its
 * asset group update and "Manage assets" actions, so saving erased the asset group's tags or
 * assets. {@link io.openaev.service.AssetGroupService} loads them inside the scoped transaction to
 * prevent it. Assets and tags are the only lazy associations an asset group serializes.
 *
 * <p>The tests run with the production list of isolated tables, read from {@code
 * application.properties}, so they follow any later activation.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the tenant-local GUC would stay alive through serialization and MASK the very failure
 * this pins. Seeding, checks and cleanup therefore run in committed transactions opened through
 * {@link TenantScopedTransaction}, scoped to the test tenant.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Asset groups of a simulation or a scenario serialize their assets and tags")
class ContextAssetGroupLinksSerializationTenantScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    Properties props = new Properties();
    try (InputStream in = new FileInputStream("src/main/resources/application.properties")) {
      props.load(in);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    String activeTables = props.getProperty("openaev.tenant.active-tables", "");
    registry.add("openaev.tenant.active-tables", () -> activeTables);
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TagComposer tagComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private TagRepository tagRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private AssetGroupRepository assetGroupRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;

  private String tenant;
  private final List<String> seededScenarios = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    tagComposer.reset();
    endpointComposer.reset();
    assetGroupComposer.reset();
    injectComposer.reset();
    exerciseComposer.reset();
    seededScenarios.clear();
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("context-asset-group-sink").getId();
  }

  @AfterEach
  void cleanup() {
    // The seeded rows were committed: delete them by id in a transaction scoped to the tenant,
    // then the tenant itself. The helper's deletes on collectors and collector_types (isolated in
    // production) join this transaction, so they run with the tenant in scope.
    inTenantScope(
        () -> {
          injectRepository.deleteAllById(
              injectComposer.generatedItems.stream().map(Inject::getId).toList());
          exerciseRepository.deleteAllById(
              exerciseComposer.generatedItems.stream().map(Exercise::getId).toList());
          scenarioRepository.deleteAllById(seededScenarios);
          assetGroupRepository.deleteAllById(
              assetGroupComposer.generatedItems.stream().map(AssetGroup::getId).toList());
          assetRepository.deleteAllById(
              endpointComposer.generatedItems.stream().map(Endpoint::getId).toList());
          tagRepository.deleteAllById(tagComposer.generatedItems.stream().map(Tag::getId).toList());
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("List the asset groups of a simulation or a scenario")
  class ListAssetGroups {

    @Test
    @DisplayName(
        "given a simulation inject targeting an asset group when the simulation asset groups are"
            + " listed then its assets and tags are serialized")
    void
        given_a_simulation_inject_targeting_an_asset_group_when_the_simulation_asset_groups_are_listed_then_its_assets_and_tags_are_serialized()
            throws Exception {
      // -- Arrange --
      SeededContext seeded = seedSimulationTargetingAnAssetGroup();

      // -- Act & Assert --
      // Without the in-scope load both arrays serialize as [] here: the post-commit open-in-view
      // load runs with app.current_tenants cleared and fails closed.
      expectEveryLazyAssociation(
          listSimulationAssetGroups(seeded.contextId()), seeded.assetGroup());
    }

    @Test
    @DisplayName(
        "given a scenario inject targeting an asset group when the scenario asset groups are listed"
            + " then its assets and tags are serialized")
    void
        given_a_scenario_inject_targeting_an_asset_group_when_the_scenario_asset_groups_are_listed_then_its_assets_and_tags_are_serialized()
            throws Exception {
      // -- Arrange --
      SeededContext seeded = seedScenarioTargetingAnAssetGroup();

      // -- Act & Assert --
      ResultActions response =
          mvc.perform(
                  get(
                          TENANT_SCENARIO_URI + "/{scenarioId}/asset-groups",
                          tenant,
                          seeded.contextId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$", hasSize(1)));
      expectEveryLazyAssociation(response, seeded.assetGroup());
    }
  }

  @Nested
  @DisplayName("Edit an asset group from the inject form")
  class EditAssetGroup {

    @Test
    @DisplayName(
        "given an asset group listed for a simulation when a tag is added through its edit form"
            + " then the existing tag is kept")
    void
        given_an_asset_group_listed_for_a_simulation_when_a_tag_is_added_through_its_edit_form_then_the_existing_tag_is_kept()
            throws Exception {
      // -- Arrange --
      SeededContext seeded = seedSimulationTargetingAnAssetGroup();
      AssetGroup assetGroup = seeded.assetGroup();
      String addedTagId = inTenantScope(() -> newTag().persist().get().getId());
      List<String> editedTags = new ArrayList<>(tagIds(assetGroup));
      editedTags.add(addedTagId);

      // -- Act --
      // Same flow as the inject form: the asset group update form is filled from the simulation
      // asset groups loaded by the validation page, then the new tag is added.
      expectEveryLazyAssociation(listSimulationAssetGroups(seeded.contextId()), assetGroup);
      AssetGroupInput input = new AssetGroupInput();
      input.setName(assetGroup.getName());
      input.setDescription(assetGroup.getDescription());
      input.setTagIds(editedTags);
      mvc.perform(
              put(ASSET_GROUP_URI + "/{assetGroupId}", assetGroup.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      inTenantScope(
          () -> {
            AssetGroup saved = assetGroupRepository.findById(assetGroup.getId()).orElseThrow();
            assertThat(tagIds(saved)).containsExactlyInAnyOrderElementsOf(editedTags);
            return null;
          });
    }

    @Test
    @DisplayName(
        "given an asset group listed for a simulation when an asset is added through manage assets"
            + " then the existing asset is kept")
    void
        given_an_asset_group_listed_for_a_simulation_when_an_asset_is_added_through_manage_assets_then_the_existing_asset_is_kept()
            throws Exception {
      // -- Arrange --
      SeededContext seeded = seedSimulationTargetingAnAssetGroup();
      AssetGroup assetGroup = seeded.assetGroup();
      String addedAssetId = inTenantScope(() -> newEndpoint().persist().get().getId());
      List<String> editedAssets = new ArrayList<>(assetIds(assetGroup));
      editedAssets.add(addedAssetId);

      // -- Act --
      // Same flow as "Manage assets": the picker starts from asset_group_assets of the stored
      // asset group, then the new asset is added. An empty list here is what dropped the existing
      // assets before the fix.
      expectEveryLazyAssociation(listSimulationAssetGroups(seeded.contextId()), assetGroup);
      UpdateAssetsOnAssetGroupInput input = new UpdateAssetsOnAssetGroupInput();
      input.setAssetIds(editedAssets);
      mvc.perform(
              put(ASSET_GROUP_URI + "/{assetGroupId}/assets", assetGroup.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      inTenantScope(
          () -> {
            AssetGroup saved = assetGroupRepository.findById(assetGroup.getId()).orElseThrow();
            assertThat(assetIds(saved)).containsExactlyInAnyOrderElementsOf(editedAssets);
            return null;
          });
    }
  }

  @Nested
  @DisplayName("Find the asset groups of a simulation or a scenario by ids")
  class FindAssetGroups {

    @Test
    @DisplayName(
        "given a simulation inject targeting an asset group when it is found by id then its tag ids"
            + " and asset ids are serialized")
    void
        given_a_simulation_inject_targeting_an_asset_group_when_it_is_found_by_id_then_its_tag_ids_and_asset_ids_are_serialized()
            throws Exception {
      // -- Arrange --
      SeededContext seeded = seedSimulationTargetingAnAssetGroup();

      // -- Act & Assert --
      // The inject form loads the asset groups it does not have yet through this endpoint, then
      // fills the asset group edit form from it: asset_group_tags must hold tag ids, as everywhere
      // else, not tag names (which matched no tag and erased them on save).
      expectEveryLazyAssociation(findAssetGroups(TENANT_EXERCISE_URI, seeded), seeded.assetGroup());
    }

    @Test
    @DisplayName(
        "given a scenario inject targeting an asset group when it is found by id then its tag ids and"
            + " asset ids are serialized")
    void
        given_a_scenario_inject_targeting_an_asset_group_when_it_is_found_by_id_then_its_tag_ids_and_asset_ids_are_serialized()
            throws Exception {
      // -- Arrange --
      SeededContext seeded = seedScenarioTargetingAnAssetGroup();

      // -- Act & Assert --
      expectEveryLazyAssociation(findAssetGroups(TENANT_SCENARIO_URI, seeded), seeded.assetGroup());
    }

    private ResultActions findAssetGroups(String contextUri, SeededContext seeded)
        throws Exception {
      return mvc.perform(
              post(contextUri + "/{contextId}/asset-groups/find", tenant, seeded.contextId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(List.of(seeded.assetGroup().getId())))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)));
    }
  }

  // -- SEEDING --

  /** The asset group targeted by an inject, and the id of the simulation or scenario around it. */
  private record SeededContext(AssetGroup assetGroup, String contextId) {}

  private SeededContext seedSimulationTargetingAnAssetGroup() {
    return inTenantScope(
        () -> {
          AssetGroupComposer.Composer assetGroupWrapper = newAssetGroup();
          Exercise exercise = createDefaultExercise();
          exercise.setTenant(new Tenant(tenant));
          injectComposer
              .forInject(newInject())
              .withAssetGroup(assetGroupWrapper)
              .withExercise(exerciseComposer.forExercise(exercise))
              .persist();
          return new SeededContext(assetGroupWrapper.get(), exercise.getId());
        });
  }

  private SeededContext seedScenarioTargetingAnAssetGroup() {
    return inTenantScope(
        () -> {
          AssetGroupComposer.Composer assetGroupWrapper = newAssetGroup();
          Scenario scenario = createDefaultCrisisScenario();
          scenario.setTenant(new Tenant(tenant));
          seededScenarios.add(scenarioRepository.save(scenario).getId());
          Inject inject = newInject();
          inject.setScenario(scenario);
          injectComposer.forInject(inject).withAssetGroup(assetGroupWrapper).persist();
          return new SeededContext(assetGroupWrapper.get(), scenario.getId());
        });
  }

  /** An asset group with one tag and one endpoint, persisted with the inject that targets it. */
  private AssetGroupComposer.Composer newAssetGroup() {
    AssetGroup assetGroup = createDefaultAssetGroup("asset-group-" + UUID.randomUUID());
    assetGroup.setTenant(new Tenant(tenant));
    assetGroup.setTags(new HashSet<>(Set.of(newTag().persist().get())));
    return assetGroupComposer.forAssetGroup(assetGroup).withAsset(newEndpoint());
  }

  private Inject newInject() {
    Inject inject = getDefaultInject();
    inject.setTitle("inject-" + UUID.randomUUID());
    inject.setTenant(new Tenant(tenant));
    return inject;
  }

  private EndpointComposer.Composer newEndpoint() {
    Endpoint endpoint = createEndpoint("endpoint-" + UUID.randomUUID());
    endpoint.setTenant(new Tenant(tenant));
    return endpointComposer.forEndpoint(endpoint);
  }

  private TagComposer.Composer newTag() {
    Tag tag = getTagWithText("asset-group-tag-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenant));
    return tagComposer.forTag(tag);
  }

  /**
   * Runs the work in a committed transaction scoped to the test tenant. The composers still read
   * the v1 thread-local tenant to attribute some rows, so it is set too.
   */
  private <T> T inTenantScope(Supplier<T> work) {
    TenantContext.setCurrentTenant(tenant);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenant), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  // -- CALLS AND ASSERTIONS --

  /** Lists the asset groups of the simulation: the seeded simulation targets only one. */
  private ResultActions listSimulationAssetGroups(String simulationId) throws Exception {
    return mvc.perform(
            get(TENANT_EXERCISE_URI + "/{exerciseId}/asset-groups", tenant, simulationId)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  private static void expectEveryLazyAssociation(ResultActions response, AssetGroup assetGroup)
      throws Exception {
    response
        .andExpect(
            jsonPath("$[0].asset_group_tags", containsInAnyOrder(tagIds(assetGroup).toArray())))
        .andExpect(
            jsonPath(
                "$[0].asset_group_assets", containsInAnyOrder(assetIds(assetGroup).toArray())));
  }

  private static List<String> tagIds(AssetGroup assetGroup) {
    return assetGroup.getTags().stream().map(Tag::getId).toList();
  }

  private static List<String> assetIds(AssetGroup assetGroup) {
    return assetGroup.getAssets().stream().map(Asset::getId).toList();
  }
}

package io.openaev.rest.inject;

import static io.openaev.rest.asset.endpoint.EndpointApi.ASSET_URI;
import static io.openaev.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectExpectationComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the "Injects played" search endpoints of the entity detail pages (assets,
 * asset groups, teams): scope correctness (direct targeting, group membership and
 * expectation-evidence targeting, scenario template exclusion) and grant-based RBAC restriction of
 * the returned injects.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Injects played search API")
class InjectsPlayedSearchApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;

  @Autowired private InjectComposer injectComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;

  @BeforeEach
  void setup() {
    injectComposer.reset();
    endpointComposer.reset();
    assetGroupComposer.reset();
    teamComposer.reset();
    exerciseComposer.reset();
    scenarioComposer.reset();
    injectExpectationComposer.reset();
  }

  private void searchAndExpectInjects(String uri, String... expectedInjectIds) throws Exception {
    var request =
        mvc.perform(
                post(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(PaginationFixture.getDefault().build()))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.totalElements").value(expectedInjectIds.length));
    for (String expectedInjectId : expectedInjectIds) {
      request.andExpect(
          jsonPath("$.content[?(@.inject_id == '%s')]".formatted(expectedInjectId)).exists());
    }
  }

  private BaseInjectExpectation detectionExpectation() {
    return InjectExpectationFixture.createExpectationWithTypeAndStatus(
        BaseInjectExpectation.EXPECTATION_TYPE.DETECTION,
        BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);
  }

  private BaseInjectExpectation manualExpectation() {
    return InjectExpectationFixture.createExpectationWithTypeAndStatus(
        BaseInjectExpectation.EXPECTATION_TYPE.MANUAL,
        BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS);
  }

  @Nested
  @DisplayName("Asset scope")
  @WithMockUser(isAdmin = true)
  class AssetScope {

    @Test
    @DisplayName("Injects directly targeting the asset are returned")
    void given_inject_directly_targeting_asset_should_return_inject() throws Exception {
      EndpointComposer.Composer endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      InjectComposer.Composer inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpoint)
              .persist();
      entityManager.flush();

      searchAndExpectInjects(
          ASSET_URI + "/" + endpoint.get().getId() + "/injects/search", inject.get().getId());
    }

    @Test
    @DisplayName("Injects targeting an asset group containing the asset are returned")
    void given_inject_targeting_group_of_asset_should_return_inject() throws Exception {
      EndpointComposer.Composer endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      AssetGroupComposer.Composer assetGroup =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("injects-played-group"))
              .withAsset(endpoint)
              .persist();
      InjectComposer.Composer inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withAssetGroup(assetGroup)
              .persist();
      entityManager.flush();

      searchAndExpectInjects(
          ASSET_URI + "/" + endpoint.get().getId() + "/injects/search", inject.get().getId());
    }

    @Test
    @DisplayName("Simulation injects evidenced only by a technical expectation are returned")
    void given_simulation_inject_with_expectation_evidence_should_return_inject() throws Exception {
      // The inject does NOT target the asset directly: only the expectation persisted at
      // execution time references it (dynamic asset-group membership case).
      EndpointComposer.Composer endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      ExerciseComposer.Composer exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist();
      InjectComposer.Composer inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExercise(exercise)
              .withExpectation(
                  injectExpectationComposer
                      .forExpectation(detectionExpectation())
                      .withEndpoint(endpoint))
              .persist();
      entityManager.flush();

      searchAndExpectInjects(
          ASSET_URI + "/" + endpoint.get().getId() + "/injects/search", inject.get().getId());
    }

    @Test
    @DisplayName("Unrelated injects and scenario template injects are excluded")
    void given_unrelated_and_scenario_injects_should_return_empty_page() throws Exception {
      EndpointComposer.Composer endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      // Inject targeting another asset
      EndpointComposer.Composer otherEndpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint("other-endpoint")).persist();
      injectComposer
          .forInject(InjectFixture.getDefaultInject())
          .withEndpoint(otherEndpoint)
          .persist();
      // Scenario template inject targeting the asset: never played, must be excluded
      scenarioComposer
          .forScenario(ScenarioFixture.getScenario())
          .withInject(
              injectComposer.forInject(InjectFixture.getDefaultInject()).withEndpoint(endpoint))
          .persist();
      entityManager.flush();

      searchAndExpectInjects(ASSET_URI + "/" + endpoint.get().getId() + "/injects/search");
    }
  }

  @Nested
  @DisplayName("Asset group scope")
  @WithMockUser(isAdmin = true)
  class AssetGroupScope {

    @Test
    @DisplayName("Direct targeting and expectation evidence are both returned")
    void given_direct_and_evidenced_injects_should_return_both() throws Exception {
      AssetGroupComposer.Composer assetGroup =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("injects-played-group"))
              .persist();
      InjectComposer.Composer directInject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withAssetGroup(assetGroup)
              .persist();
      ExerciseComposer.Composer exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist();
      InjectComposer.Composer evidencedInject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExercise(exercise)
              .withExpectation(
                  injectExpectationComposer
                      .forExpectation(detectionExpectation())
                      .withAssetGroup(assetGroup))
              .persist();
      entityManager.flush();

      searchAndExpectInjects(
          ASSET_GROUP_URI + "/" + assetGroup.get().getId() + "/injects/search",
          directInject.get().getId(),
          evidencedInject.get().getId());
    }
  }

  @Nested
  @DisplayName("Team scope")
  @WithMockUser(isAdmin = true)
  class TeamScope {

    @Test
    @DisplayName("Direct targeting and table-top expectation evidence are both returned")
    void given_direct_and_evidenced_injects_should_return_both() throws Exception {
      TeamComposer.Composer team = teamComposer.forTeam(TeamFixture.getDefaultTeam()).persist();
      InjectComposer.Composer directInject =
          injectComposer.forInject(InjectFixture.getDefaultInject()).withTeam(team).persist();
      ExerciseComposer.Composer exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist();
      InjectComposer.Composer evidencedInject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withExercise(exercise)
              .withExpectation(
                  injectExpectationComposer.forExpectation(manualExpectation()).withTeam(team))
              .persist();
      entityManager.flush();

      searchAndExpectInjects(
          TEAM_URI + "/" + team.get().getId() + "/injects/search",
          directInject.get().getId(),
          evidencedInject.get().getId());
    }
  }

  @Nested
  @DisplayName("RBAC")
  class Rbac {

    @Test
    @DisplayName("Without grants, injects in scope are not returned")
    @WithMockUser(withCapabilities = {Capability.ACCESS_ASSETS})
    void given_no_grants_should_return_empty_page() throws Exception {
      EndpointComposer.Composer endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      // One atomic testing and one simulation inject, both targeting the asset
      injectComposer.forInject(InjectFixture.getDefaultInject()).withEndpoint(endpoint).persist();
      ExerciseComposer.Composer exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist();
      injectComposer
          .forInject(InjectFixture.getDefaultInject())
          .withEndpoint(endpoint)
          .withExercise(exercise)
          .persist();
      entityManager.flush();

      searchAndExpectInjects(ASSET_URI + "/" + endpoint.get().getId() + "/injects/search");
    }

    @Test
    @DisplayName("An OBSERVER grant on the simulation restricts results to its injects")
    @WithMockUser(withCapabilities = {Capability.ACCESS_ASSETS})
    void given_observer_grant_on_simulation_should_return_only_simulation_injects()
        throws Exception {
      EndpointComposer.Composer endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      // Atomic testing inject: no grant, must stay hidden
      injectComposer.forInject(InjectFixture.getDefaultInject()).withEndpoint(endpoint).persist();
      // Simulation inject: OBSERVER grant on the simulation makes it visible
      ExerciseComposer.Composer exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist();
      InjectComposer.Composer simulationInject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpoint)
              .withExercise(exercise)
              .persist();
      entityManager.flush();

      addGrantToCurrentUser(
          Grant.GRANT_RESOURCE_TYPE.SIMULATION, Grant.GRANT_TYPE.OBSERVER, exercise.get().getId());

      searchAndExpectInjects(
          ASSET_URI + "/" + endpoint.get().getId() + "/injects/search",
          simulationInject.get().getId());
    }

    @Test
    @DisplayName("An admin sees every inject in scope")
    @WithMockUser(isAdmin = true)
    void given_admin_should_return_all_injects_in_scope() throws Exception {
      EndpointComposer.Composer endpoint =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
      InjectComposer.Composer atomicInject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpoint)
              .persist();
      ExerciseComposer.Composer exercise =
          exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist();
      InjectComposer.Composer simulationInject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpoint)
              .withExercise(exercise)
              .persist();
      entityManager.flush();

      searchAndExpectInjects(
          ASSET_URI + "/" + endpoint.get().getId() + "/injects/search",
          atomicInject.get().getId(),
          simulationInject.get().getId());
    }
  }
}

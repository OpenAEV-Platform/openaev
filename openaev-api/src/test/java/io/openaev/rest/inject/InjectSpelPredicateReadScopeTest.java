package io.openaev.rest.inject;

import static io.openaev.rest.asset.endpoint.EndpointApi.ENDPOINT_URI;
import static io.openaev.rest.asset_group.AssetGroupApi.ASSET_GROUP_URI;
import static io.openaev.rest.challenge.ChallengeApi.CHALLENGE_URI;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Injector;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.rest.challenge.form.ChallengeInput;
import io.openaev.rest.challenge.form.FlagInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.ChallengeFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The 8 native/JPQL queries that keep a v1 {@code i.tenant_id = :#{#tenantContext.currentTenant}}
 * SpEL predicate alongside {@code injects}, {@code exercises} (and the already-active {@code
 * assets}/{@code asset_groups}/{@code findings}) tables, read through the non-prefixed route.
 *
 * <p>The caller is a member of tenant B only, naming B alone in {@code X-Tenant-Ids}: the ambient
 * tenant (v1) stays the default one, exactly as production leaves it on this route. Before the
 * predicate is dropped, it ANDs an extra {@code tenant_id = <ambient>} restriction onto the
 * inspector's own, correct {@code can_access_tenant} rewrite, so B's own rows are filtered out by
 * the leftover default-tenant check and the read comes back empty. Dropping the predicate lets the
 * inspector's request-scoped rewrite alone decide, and B's rows come back.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("The 8 v1 SpEL tenant predicates read correctly once injects is active")
class InjectSpelPredicateReadScopeTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Resource private ObjectMapper objectMapper;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    injectComposer.reset();
    exerciseComposer.reset();
    endpointComposer.reset();
    assetGroupComposer.reset();
    findingComposer.reset();
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("inject-spel-predicate-b").getId();
  }

  @Nested
  @DisplayName("InjectRepository#findAllIdsForChallengeId (single-table native, no join)")
  @WithMockUser(isAdmin = true)
  class ChallengeIdLookup {

    @Test
    @DisplayName("given a challenge played in B should enrich it with B's simulation on B's route")
    void given_challengePlayedInB_should_enrichWithBSimulation() throws Exception {
      // -- ARRANGE --
      // A plain injector (no payload support, which otherwise cascades its own companion
      // contract) and a contract carrying the exact id the query filters on, both stamped to B,
      // linked explicitly: this sidesteps the payload-injector fixture's unrelated cascade.
      Injector injector =
          InjectorFixture.createInjector(
              UUID.randomUUID().toString(), "spel-predicate-injector", "spel-predicate");
      injector.setTenantId(tenantB);
      injectorRepository.save(injector);

      InjectorContract contract = new InjectorContract();
      contract.setId(InjectRepository.CHALLENGE_PUBLISH_CONTRACT_ID);
      contract.setTenant(new Tenant(tenantB));
      contract.setContent("{}");
      contract.addInjector(injector);
      contract = injectorContractRepository.save(contract);

      Exercise exercise = ExerciseFixture.getExercise();
      exercise.setTenant(new Tenant(tenantB));
      Exercise persistedExercise = exerciseComposer.forExercise(exercise).persist().get();

      Challenge challenge = ChallengeFixture.createDefaultChallenge();
      // The update endpoint clears and rebuilds the flags list in place: the fixture's immutable
      // List.of(...) throws UnsupportedOperationException on that clear().
      challenge.setFlags(new ArrayList<>(challenge.getFlags()));
      challenge.setTenant(new Tenant(tenantB));
      Challenge persistedChallenge = challengeRepository.save(challenge);

      Inject inject =
          InjectFixture.createDefaultInjectChallenge(
              contract, objectMapper, List.of(persistedChallenge.getId()));
      inject.setTenant(new Tenant(tenantB));
      inject.setExercise(persistedExercise);
      injectComposer.forInject(inject).persist();
      entityManager.flush();

      // -- ACT --
      String response =
          mvc.perform(inB(put(CHALLENGE_URI + "/" + persistedChallenge.getId()), updateInput()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThat(JsonPath.<List<String>>read(response, "$.challenge_exercises"))
          .containsExactly(persistedExercise.getId());
    }

    private ChallengeInput updateInput() {
      FlagInput flag = new FlagInput();
      flag.setType("VALUE");
      flag.setValue("secret-flag");
      return new ChallengeInput(
          "Updated name", "category", "content", 100.0, 3, List.of(), List.of(), List.of(flag));
    }
  }

  @Nested
  @DisplayName(
      "InjectRepository#findAllByTitleLinkedToFindings(WithContext) (native, inject+findings join)")
  @WithMockUser(isAdmin = true)
  class InjectFindingsOptions {

    @Test
    @DisplayName("given a finding on B's inject should list it from B's route with no source id")
    void given_findingOnBInject_should_listWithNoSourceId() throws Exception {
      // -- ARRANGE --
      Inject inject = bInjectWithFinding();

      // -- ACT --
      String response =
          mvc.perform(inB(get(INJECT_URI + "/findings/options")))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThat(JsonPath.<List<String>>read(response, "$[*].id")).contains(inject.getId());
    }

    @Test
    @DisplayName("given a finding on B's inject should list it from B's route with a source id")
    void given_findingOnBInject_should_listWithSourceId() throws Exception {
      // -- ARRANGE --
      Inject inject = bInjectWithFinding();

      // -- ACT --
      String response =
          mvc.perform(
                  inB(get(INJECT_URI + "/findings/options"))
                      .queryParam("sourceId", inject.getExercise().getId()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThat(JsonPath.<List<String>>read(response, "$[*].id")).contains(inject.getId());
    }

    private Inject bInjectWithFinding() {
      Exercise exercise = ExerciseFixture.getExercise();
      exercise.setTenant(new Tenant(tenantB));
      Exercise persistedExercise = exerciseComposer.forExercise(exercise).persist().get();

      Inject inject = InjectFixture.getDefaultInject();
      inject.setTenant(new Tenant(tenantB));
      inject.setExercise(persistedExercise);
      InjectComposer.Composer injectComposerResult = injectComposer.forInject(inject).persist();

      Finding finding = FindingFixture.createDefaultTextFinding();
      finding.setTenant(new Tenant(tenantB));
      findingComposer.forFinding(finding).withInject(injectComposerResult).persist();
      entityManager.flush();
      return injectComposerResult.get();
    }
  }

  @Nested
  @DisplayName(
      "InjectRepository#findContentTargetContentsByExerciseIds (single-table native, no join)")
  @WithMockUser(isAdmin = true)
  class ExerciseContentTargets {

    @Test
    @DisplayName("given a manual target on B's inject should resolve it from B's route")
    void given_manualTargetOnBInject_should_resolveFromBRoute() throws Exception {
      // -- ARRANGE --
      Exercise exercise = ExerciseFixture.getExercise();
      exercise.setTenant(new Tenant(tenantB));
      Exercise persistedExercise = exerciseComposer.forExercise(exercise).persist().get();

      Inject inject = InjectFixture.getDefaultInject();
      inject.setTenant(new Tenant(tenantB));
      inject.setExercise(persistedExercise);
      inject.setContent(
          (ObjectNode)
              objectMapper.readTree(
                  "{\"target_selector\":\"manual\",\"manual_targets\":\"10.0.0.1\"}"));
      injectComposer.forInject(inject).persist();
      entityManager.flush();

      // -- ACT --
      mvc.perform(inB(post(EXERCISE_URI + "/search"), largeSearchBody()))
          .andExpect(status().isOk());

      // -- ASSERT -- (same transaction/connection: app.current_tenants is still B's alone)
      List<Object[]> rows =
          injectRepository.findContentTargetContentsByExerciseIds(
              Set.of(persistedExercise.getId()));
      assertThat(rows).extracting(row -> row[0]).contains(persistedExercise.getId());
    }
  }

  @Nested
  @DisplayName(
      "ExerciseRepository#findAllOptionByNameLinkedToFindings(WithContext) (native, inject+findings+exercises join)")
  @WithMockUser(isAdmin = true)
  class ExerciseFindingsOptions {

    @Test
    @DisplayName(
        "given a finding on B's simulation should list it from B's route with no source id")
    void given_findingOnBSimulation_should_listWithNoSourceId() throws Exception {
      // -- ARRANGE --
      Exercise exercise = bExerciseWithFinding();

      // -- ACT --
      String response =
          mvc.perform(inB(get(EXERCISE_URI + "/findings/options")))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThat(JsonPath.<List<String>>read(response, "$[*].id")).contains(exercise.getId());
    }

    @Test
    @DisplayName("given a finding on B's simulation should list it from B's route with a source id")
    void given_findingOnBSimulation_should_listWithSourceId() throws Exception {
      // -- ARRANGE --
      Exercise exercise = ExerciseFixture.getExercise();
      exercise.setTenant(new Tenant(tenantB));
      Exercise persistedExercise = exerciseComposer.forExercise(exercise).persist().get();

      Inject inject = InjectFixture.getDefaultInject();
      inject.setTenant(new Tenant(tenantB));
      inject.setExercise(persistedExercise);
      InjectComposer.Composer injectComposerResult = injectComposer.forInject(inject).persist();

      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setTenant(new Tenant(tenantB));
      EndpointComposer.Composer endpointComposerResult = endpointComposer.forEndpoint(endpoint);

      Finding finding = FindingFixture.createDefaultTextFinding();
      finding.setTenant(new Tenant(tenantB));
      findingComposer
          .forFinding(finding)
          .withInject(injectComposerResult)
          .withEndpoint(endpointComposerResult)
          .persist();
      entityManager.flush();

      // -- ACT --
      String response =
          mvc.perform(
                  inB(get(EXERCISE_URI + "/findings/options"))
                      .queryParam("scenarioId", endpointComposerResult.get().getId()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThat(JsonPath.<List<String>>read(response, "$[*].id"))
          .contains(persistedExercise.getId());
    }

    private Exercise bExerciseWithFinding() {
      Exercise exercise = ExerciseFixture.getExercise();
      exercise.setTenant(new Tenant(tenantB));
      Exercise persistedExercise = exerciseComposer.forExercise(exercise).persist().get();

      Inject inject = InjectFixture.getDefaultInject();
      inject.setTenant(new Tenant(tenantB));
      inject.setExercise(persistedExercise);
      InjectComposer.Composer injectComposerResult = injectComposer.forInject(inject).persist();

      Finding finding = FindingFixture.createDefaultTextFinding();
      finding.setTenant(new Tenant(tenantB));
      findingComposer.forFinding(finding).withInject(injectComposerResult).persist();
      entityManager.flush();
      return persistedExercise;
    }
  }

  @Nested
  @DisplayName(
      "EndpointRepository#findAllBySimulationOrScenarioIdAndName (JPQL, Inject join Endpoint)")
  @WithMockUser(isAdmin = true)
  class EndpointOptionsBySimulationOrScenario {

    @Test
    @DisplayName("given an endpoint targeted in B should list it from B's route")
    void given_endpointTargetedInB_should_listFromBRoute() throws Exception {
      // -- ARRANGE --
      Exercise exercise = ExerciseFixture.getExercise();
      exercise.setTenant(new Tenant(tenantB));
      Exercise persistedExercise = exerciseComposer.forExercise(exercise).persist().get();

      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setTenant(new Tenant(tenantB));
      Endpoint persistedEndpoint = endpointComposer.forEndpoint(endpoint).persist().get();

      Inject inject = InjectFixture.getDefaultInject();
      inject.setTenant(new Tenant(tenantB));
      inject.setExercise(persistedExercise);
      inject.setAssets(new ArrayList<>(List.of(persistedEndpoint)));
      injectComposer.forInject(inject).persist();
      entityManager.flush();

      // -- ACT --
      String response =
          mvc.perform(
                  inB(get(ENDPOINT_URI + "/options"))
                      .queryParam("sourceId", persistedExercise.getId())
                      .queryParam("inputFilterOption", "SIMULATION_OR_SCENARIO"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThat(JsonPath.<List<String>>read(response, "$[*].id"))
          .contains(persistedEndpoint.getId());
    }
  }

  @Nested
  @DisplayName(
      "AssetGroupRepository#findAllBySimulationOrScenarioIdAndName (JPQL, Inject join AssetGroup)")
  @WithMockUser(isAdmin = true)
  class AssetGroupOptionsBySimulationOrScenario {

    @Test
    @DisplayName("given an asset group targeted in B should list it from B's route")
    void given_assetGroupTargetedInB_should_listFromBRoute() throws Exception {
      // -- ARRANGE --
      Exercise exercise = ExerciseFixture.getExercise();
      exercise.setTenant(new Tenant(tenantB));
      Exercise persistedExercise = exerciseComposer.forExercise(exercise).persist().get();

      AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup("b-asset-group");
      assetGroup.setTenant(new Tenant(tenantB));
      AssetGroup persistedAssetGroup = assetGroupComposer.forAssetGroup(assetGroup).persist().get();

      Inject inject = InjectFixture.getDefaultInject();
      inject.setTenant(new Tenant(tenantB));
      inject.setExercise(persistedExercise);
      inject.setAssetGroups(new ArrayList<>(List.of(persistedAssetGroup)));
      injectComposer.forInject(inject).persist();
      entityManager.flush();

      // -- ACT --
      String response =
          mvc.perform(
                  inB(get(ASSET_GROUP_URI + "/options"))
                      .queryParam("sourceId", persistedExercise.getId())
                      .queryParam("inputFilterOption", "SIMULATION_OR_SCENARIO"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertThat(JsonPath.<List<String>>read(response, "$[*].id"))
          .contains(persistedAssetGroup.getId());
    }
  }

  // -- HELPERS --

  /** B's route, naming B alone in the header: the ambient tenant stays the default one. */
  private MockHttpServletRequestBuilder inB(MockHttpServletRequestBuilder request) {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return request.header(TENANT_IDS_HEADER, tenantB).accept(MediaType.APPLICATION_JSON);
  }

  private MockHttpServletRequestBuilder inB(MockHttpServletRequestBuilder request, Object body)
      throws Exception {
    return inB(request)
        .content(asJsonString(body))
        .contentType(MediaType.APPLICATION_JSON)
        .with(csrf());
  }

  private Object largeSearchBody() {
    return PaginationFixture.getDefault().size(100).textSearch("").build();
  }
}

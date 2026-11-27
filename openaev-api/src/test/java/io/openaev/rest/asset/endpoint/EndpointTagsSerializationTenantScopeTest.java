package io.openaev.rest.asset.endpoint;

import static io.openaev.rest.asset.endpoint.EndpointApi.TENANT_ENDPOINT_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.EndpointFixture.createEndpoint;
import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The endpoint list and find routes, and the endpoints of a simulation or a scenario, used to
 * return the {@code Endpoint} entity. Its lazy {@code asset_tags} was then read when the JSON was
 * written, after the request transaction had committed and the tenant scope was cleared, so the tag
 * lookup read zero rows and the response said {@code []}. These routes now map each endpoint to its
 * output inside the transaction.
 *
 * <p>The class is deliberately NOT {@code Transactional}: a rolled-back test transaction never
 * commits, so its scope would stay alive through serialization and hide the very failure this pins.
 * Seeding runs in committed transactions scoped to the test tenant, and the tenant and its rows are
 * deleted after each test.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Endpoint responses keep the tags of each endpoint")
class EndpointTagsSerializationTenantScopeTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private TagComposer tagComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;

  private String tenantId;

  @BeforeEach
  void setUp() throws Exception {
    tagComposer.reset();
    endpointComposer.reset();
    injectComposer.reset();
    exerciseComposer.reset();
    scenarioComposer.reset();
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenantId =
        tenantHelper.createTenantWithCurrentUser("endpoint-tags-" + UUID.randomUUID()).getId();
  }

  @AfterEach
  void cleanup() {
    // The scenario reply-to addresses have no ON DELETE CASCADE, so the tenant delete would fail on
    // them: the scenarios are deleted first, and Hibernate removes their addresses with them.
    inTenant(
        () -> {
          scenarioRepository.deleteAll(scenarioComposer.generatedItems);
          return null;
        });
    tenantHelper.deleteCommittedTenants(tenantId);
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given a tagged endpoint, when the endpoints are listed, should answer its tags")
  void given_taggedEndpoint_should_answerItsTagsInTheList() throws Exception {
    // -- Arrange --
    Endpoint endpoint = inTenant(() -> taggedEndpoint().persist().get());

    // -- Act --
    String response = call(get(TENANT_ENDPOINT_URI, tenantId));

    // -- Assert --
    assertThat(tagsOf(response, endpoint)).containsExactlyInAnyOrderElementsOf(tagIds(endpoint));
  }

  @Test
  @DisplayName("given a tagged endpoint, when it is found by id, should answer its tags")
  void given_taggedEndpoint_should_answerItsTagsWhenFoundById() throws Exception {
    // -- Arrange --
    Endpoint endpoint = inTenant(() -> taggedEndpoint().persist().get());

    // -- Act --
    String response =
        call(
            post(TENANT_ENDPOINT_URI + "/find", tenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(List.of(endpoint.getId()))));

    // -- Assert --
    assertThat(tagsOf(response, endpoint)).containsExactlyInAnyOrderElementsOf(tagIds(endpoint));
  }

  @Test
  @DisplayName(
      "given a simulation targeting a tagged endpoint, when its endpoints are listed, should answer"
          + " the endpoint tags")
  void given_simulationTargetingTaggedEndpoint_should_answerTheEndpointTags() throws Exception {
    // -- Arrange --
    EndpointComposer.Composer endpoint = taggedEndpoint();
    Exercise simulation = ExerciseFixture.createDefaultExercise();
    simulation.setTenant(new Tenant(tenantId));
    String simulationId =
        inTenant(
            () ->
                exerciseComposer
                    .forExercise(simulation)
                    .withInject(injectTargeting(endpoint))
                    .persist()
                    .get()
                    .getId());

    // -- Act --
    String response =
        call(get(TENANT_EXERCISE_URI + "/{exerciseId}/endpoints", tenantId, simulationId));

    // -- Assert --
    assertThat(tagsOf(response, endpoint.get()))
        .containsExactlyInAnyOrderElementsOf(tagIds(endpoint.get()));
  }

  @Test
  @DisplayName(
      "given a scenario targeting a tagged endpoint, when its endpoints are listed, should answer"
          + " the endpoint tags")
  void given_scenarioTargetingTaggedEndpoint_should_answerTheEndpointTags() throws Exception {
    // -- Arrange --
    EndpointComposer.Composer endpoint = taggedEndpoint();
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setTenant(new Tenant(tenantId));
    String scenarioId =
        inTenant(
            () ->
                scenarioComposer
                    .forScenario(scenario)
                    .withInject(injectTargeting(endpoint))
                    .persist()
                    .get()
                    .getId());

    // -- Act --
    String response =
        call(get(TENANT_SCENARIO_URI + "/{scenarioId}/endpoints", tenantId, scenarioId));

    // -- Assert --
    assertThat(tagsOf(response, endpoint.get()))
        .containsExactlyInAnyOrderElementsOf(tagIds(endpoint.get()));
  }

  // -- SEEDING --

  /** An endpoint of the test tenant carrying two tags, not persisted yet. */
  private EndpointComposer.Composer taggedEndpoint() {
    Endpoint endpoint = createEndpoint("endpoint-tags-" + UUID.randomUUID());
    endpoint.setTenant(new Tenant(tenantId));
    return endpointComposer.forEndpoint(endpoint).withTag(tag()).withTag(tag());
  }

  private InjectComposer.Composer injectTargeting(EndpointComposer.Composer endpoint) {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTitle("endpoint-tags-" + UUID.randomUUID());
    inject.setTenant(new Tenant(tenantId));
    return injectComposer.forInject(inject).withEndpoint(endpoint);
  }

  private TagComposer.Composer tag() {
    Tag tag = getTagWithText("endpoint-tags-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenantId));
    return tagComposer.forTag(tag);
  }

  /**
   * Runs the work in a committed transaction scoped to the test tenant. The composers read the v1
   * thread-local tenant to attribute some rows, so it is set too.
   */
  private <T> T inTenant(Supplier<T> work) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  // -- CALLS --

  private String call(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.accept(MediaType.APPLICATION_JSON).with(csrf()))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /** The tags the response gives for this endpoint, found by its id in the returned list. */
  private static List<String> tagsOf(String response, Endpoint endpoint) {
    return JsonPath.read(response, "$[?(@.asset_id == '" + endpoint.getId() + "')].asset_tags[*]");
  }

  private static List<String> tagIds(Endpoint endpoint) {
    return endpoint.getTags().stream().map(Tag::getId).toList();
  }
}

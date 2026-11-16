package io.openaev.rest.inject;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.inject.InjectApi.TENANT_INJECT_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.rest.exercise.form.ExerciseTeamPlayersEnableInput;
import io.openaev.rest.exercise.form.ScenarioTeamPlayersEnableInput;
import io.openaev.rest.inject.form.InjectReceptionInput;
import io.openaev.rest.inject.form.InjectTeamsInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectStatusFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.composers.DocumentComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A scenario, a simulation or an inject returned as a response serializes its lazy {@code tags} and
 * {@code documents} open-in-view, after the request transaction has committed. Both tables are
 * isolated, and the tenant scope lives only as long as the transaction, so a collection loaded at
 * that point reads zero rows and the response says {@code []} while the stored row carries the
 * links. Every response below must therefore load them inside the request's scoped transaction.
 *
 * <p>The class runs with the production list of isolated tables. It is deliberately NOT {@code
 * Transactional}: a rolled-back test transaction never commits, so its scope would stay alive
 * through serialization and hide the very failure this pins. Seeding runs in committed transactions
 * scoped to the test tenant, and the tenant and its rows are deleted after each test.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Responses load the lazy collections of isolated tables inside the request scope")
class ResponseCollectionsTenantScopeTest extends ProductionTablesIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;
  @Autowired private TagComposer tagComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;

  private String tenantId;
  private String otherTenantId;
  private String tagId;
  private String documentId;

  @BeforeEach
  void seedTenant() throws Exception {
    tagComposer.reset();
    documentComposer.reset();
    scenarioComposer.reset();
    exerciseComposer.reset();
    injectComposer.reset();
    injectStatusComposer.reset();
    otherTenantId = null;
    tenantId =
        tenantHelper
            .createTenantWithCurrentUser("response-collections-" + UUID.randomUUID())
            .getId();
  }

  @AfterEach
  void cleanup() {
    // No ON DELETE CASCADE from the scenario reply-to addresses: removed before the tenant.
    new JdbcTemplate(dataSource)
        .update(
            "DELETE FROM scenario_mails_reply_to WHERE scenario_id IN"
                + " (SELECT scenario_id FROM scenarios WHERE tenant_id = ?)",
            tenantId);
    tenantHelper.deleteCommittedTenants(tenantId, otherTenantId);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Scenario responses")
  class ScenarioResponses {

    @Test
    @DisplayName("given a tagged scenario with a document, should answer its tags and document")
    void given_taggedScenarioWithDocument_should_answerItsTagsAndDocument() throws Exception {
      // -- ARRANGE --
      String scenarioId = inTenant(() -> taggedScenarioWithDocument().persist().get().getId());

      // -- ACT --
      String response =
          call(
              put(
                      TENANT_SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/disable",
                      tenantId,
                      scenarioId,
                      UUID.randomUUID().toString())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new ScenarioTeamPlayersEnableInput())));

      // -- ASSERT --
      assertThat(ids(response, "$.scenario_tags")).containsExactly(tagId);
      assertThat(ids(response, "$.scenario_documents")).containsExactly(documentId);
    }

    @Test
    @DisplayName(
        "given the scenario of a simulation is read, should answer the scenario's tags and"
            + " document")
    void given_scenarioOfASimulation_should_answerItsTagsAndDocument() throws Exception {
      // -- ARRANGE --
      List<String> ids = new ArrayList<>();
      inTenant(
          () -> {
            ExerciseComposer.Composer simulation =
                exerciseComposer.forExercise(simulationInTenant());
            taggedScenarioWithDocument().withSimulation(simulation).persist();
            ids.add(simulation.get().getId());
            return null;
          });

      // -- ACT --
      String response =
          call(get(TENANT_EXERCISE_URI + "/{simulationId}/scenario", tenantId, ids.get(0)));

      // -- ASSERT --
      assertThat(ids(response, "$.scenario_tags")).containsExactly(tagId);
      assertThat(ids(response, "$.scenario_documents")).containsExactly(documentId);
    }

    @Test
    @DisplayName(
        "given a scenario linked to a tag of another tenant, should answer its own tag only")
    void given_scenarioLinkedToATagOfAnotherTenant_should_answerItsOwnTagOnly() throws Exception {
      // -- ARRANGE --
      String scenarioId = inTenant(() -> taggedScenarioWithDocument().persist().get().getId());
      String ownTagId = tagId;
      otherTenantId = tenantHelper.createTenant("response-collections-other").getId();
      String foreignTagId =
          inTenant(otherTenantId, () -> tagComposer.forTag(tagOf(otherTenantId)).persist().get())
              .getId();
      // scenarios_tags carries no tenant_id: a link to another tenant's tag is a plain row.
      new JdbcTemplate(dataSource)
          .update(
              "INSERT INTO scenarios_tags (scenario_id, tag_id) VALUES (?, ?)",
              scenarioId,
              foreignTagId);

      // -- ACT --
      String response =
          call(
              put(
                      TENANT_SCENARIO_URI + "/{scenarioId}/teams/{teamId}/players/disable",
                      tenantId,
                      scenarioId,
                      UUID.randomUUID().toString())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new ScenarioTeamPlayersEnableInput())));

      // -- ASSERT --
      assertThat(ids(response, "$.scenario_tags")).containsExactly(ownTagId);
    }
  }

  @Nested
  @DisplayName("Simulation responses")
  class SimulationResponses {

    @Test
    @DisplayName("given a tagged simulation with a document, should answer its tags and document")
    void given_taggedSimulationWithDocument_should_answerItsTagsAndDocument() throws Exception {
      // -- ARRANGE --
      String simulationId =
          inTenant(
              () ->
                  exerciseComposer
                      .forExercise(simulationInTenant())
                      .withTag(tag())
                      .withDocument(document())
                      .persist()
                      .get()
                      .getId());

      // -- ACT --
      String response =
          call(
              put(
                      TENANT_EXERCISE_URI + "/{exerciseId}/teams/{teamId}/players/disable",
                      tenantId,
                      simulationId,
                      UUID.randomUUID().toString())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(new ExerciseTeamPlayersEnableInput())));

      // -- ASSERT --
      assertThat(ids(response, "$.exercise_tags")).containsExactly(tagId);
      assertThat(ids(response, "$.exercise_documents")).containsExactly(documentId);
    }
  }

  @Nested
  @DisplayName("Inject responses")
  class InjectResponses {

    @Test
    @DisplayName("given a tagged simulation inject is read, should answer its tags")
    void given_taggedSimulationInjectRead_should_answerItsTags() throws Exception {
      // -- ARRANGE --
      Inject inject = inTenant(() -> taggedInject().withExercise(simulation()).persist().get());

      // -- ACT --
      String response =
          call(
              get(
                  TENANT_EXERCISE_URI + "/{exerciseId}/injects/{injectId}",
                  tenantId,
                  inject.getExercise().getId(),
                  inject.getId()));

      // -- ASSERT --
      assertThat(ids(response, "$.inject_tags")).containsExactly(tagId);
    }

    @Test
    @DisplayName("given the teams of a tagged simulation inject are set, should answer its tags")
    void given_teamsOfATaggedSimulationInjectSet_should_answerItsTags() throws Exception {
      // -- ARRANGE --
      Inject inject = inTenant(() -> taggedInject().withExercise(simulation()).persist().get());
      InjectTeamsInput input = new InjectTeamsInput();
      input.setTeamIds(List.of());

      // -- ACT --
      String response =
          call(
              put(
                      TENANT_EXERCISE_URI + "/{exerciseId}/injects/{injectId}/teams",
                      tenantId,
                      inject.getExercise().getId(),
                      inject.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)));

      // -- ASSERT --
      assertThat(ids(response, "$.inject_tags")).containsExactly(tagId);
    }

    @Test
    @DisplayName("given a tagged scenario inject is read, should answer its tags")
    void given_taggedScenarioInjectRead_should_answerItsTags() throws Exception {
      // -- ARRANGE --
      List<String> ids = scenarioWithTaggedInject();

      // -- ACT --
      String response =
          call(
              get(
                  TENANT_SCENARIO_URI + "/{scenarioId}/injects/{injectId}",
                  tenantId,
                  ids.get(0),
                  ids.get(1)));

      // -- ASSERT --
      assertThat(ids(response, "$.inject_tags")).containsExactly(tagId);
    }

    @Test
    @DisplayName("given the reception of a tagged inject, should answer its tags")
    void given_receptionOfATaggedInject_should_answerItsTags() throws Exception {
      // -- ARRANGE --
      String injectId =
          inTenant(
              () ->
                  taggedInject()
                      .withExercise(simulation())
                      .withInjectStatus(
                          injectStatusComposer.forInjectStatus(
                              InjectStatusFixture.createDraftInjectStatus()))
                      .persist()
                      .get()
                      .getId());
      InjectReceptionInput input = new InjectReceptionInput();
      input.setTrackingTotalCount(1);

      // -- ACT --
      String response =
          call(
              post(TENANT_INJECT_URI + "/execution/reception/{injectId}", tenantId, injectId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)));

      // -- ASSERT --
      assertThat(ids(response, "$.inject_tags")).containsExactly(tagId);
    }

    @Test
    @DisplayName("given a tagged inject of a scheduled simulation, should list it with its tags")
    void given_taggedInjectOfAScheduledSimulation_should_listItWithItsTags() throws Exception {
      // -- ARRANGE --
      String injectId =
          inTenant(
              () -> {
                Exercise scheduled =
                    ExerciseFixture.createDefaultIncidentResponseExercise(
                        Instant.now().plus(1, ChronoUnit.HOURS));
                scheduled.setTenant(new Tenant(tenantId));
                return taggedInject()
                    .withExercise(exerciseComposer.forExercise(scheduled))
                    .persist()
                    .get()
                    .getId();
              });

      // -- ACT --
      String response = call(get(TENANT_INJECT_URI + "/next", tenantId));

      // -- ASSERT --
      assertThat(ids(response, "$[?(@.inject_id == '" + injectId + "')].inject_tags[*]"))
          .containsExactly(tagId);
    }
  }

  // -- SEEDING --

  /** A scenario carrying a tag and a document of the tenant; not persisted yet. */
  private ScenarioComposer.Composer taggedScenarioWithDocument() {
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setTenant(new Tenant(tenantId));
    scenario.setTags(new HashSet<>(Set.of(tag().get())));
    scenario.setDocuments(new ArrayList<>(List.of(document().get())));
    return scenarioComposer.forScenario(scenario);
  }

  /** A scenario and its tagged inject, persisted: their ids, scenario first. */
  private List<String> scenarioWithTaggedInject() {
    return inTenant(
        () -> {
          InjectComposer.Composer inject = taggedInject();
          Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
          scenario.setTenant(new Tenant(tenantId));
          String scenarioId =
              scenarioComposer.forScenario(scenario).withInject(inject).persist().get().getId();
          return List.of(scenarioId, inject.get().getId());
        });
  }

  private InjectComposer.Composer taggedInject() {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTitle("response-collections-" + UUID.randomUUID());
    inject.setTenant(new Tenant(tenantId));
    return injectComposer.forInject(inject).withTag(tag());
  }

  private ExerciseComposer.Composer simulation() {
    return exerciseComposer.forExercise(simulationInTenant());
  }

  private Exercise simulationInTenant() {
    Exercise simulation = ExerciseFixture.createDefaultExercise();
    simulation.setTenant(new Tenant(tenantId));
    return simulation;
  }

  private TagComposer.Composer tag() {
    TagComposer.Composer composer = tagComposer.forTag(tagOf(tenantId));
    tagId = composer.persist().get().getId();
    return composer;
  }

  private static Tag tagOf(String tenant) {
    Tag tag = TagFixture.getTagWithText("response-collections-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenant));
    return tag;
  }

  private DocumentComposer.Composer document() {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setTenant(new Tenant(tenantId));
    DocumentComposer.Composer composer = documentComposer.forDocument(document);
    documentId = composer.persist().get().getId();
    return composer;
  }

  /**
   * Runs the work in a committed transaction scoped to the test tenant. The composers read the v1
   * thread-local tenant to attribute some rows, so it is set too.
   */
  private <T> T inTenant(Supplier<T> work) {
    return inTenant(tenantId, work);
  }

  private <T> T inTenant(String tenant, Supplier<T> work) {
    TenantContext.setCurrentTenant(tenant);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenant), work);
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  // -- CALLS --

  private String call(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static List<String> ids(String response, String path) {
    return JsonPath.read(response, path);
  }
}

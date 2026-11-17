package io.openaev.rest.tag;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TagLinkJdbc.column;
import static io.openaev.utils.TagLinkJdbc.saveTag;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.exercise.form.ExerciseUpdateTagsInput;
import io.openaev.rest.exercise.form.LogCreateInput;
import io.openaev.rest.exercise.form.UpdateExerciseInput;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.scenario.form.ScenarioUpdateTagsInput;
import io.openaev.rest.scenario.form.UpdateScenarioInput;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
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
import org.springframework.transaction.annotation.Transactional;

/**
 * A simulation, a simulation log or a scenario links only the tags of its own tenant when an edit
 * names tags of several tenants.
 *
 * <p>The simulation and the scenario live in the default tenant, the one the ambient thread-local
 * falls back to. On the non-prefixed route {@code X-Tenant-Ids} can name several tenants, and these
 * edits carry no tenant selector, so the request scope holds all of them. The tag ids of the body
 * resolve in the tenant of the entity written alone. The links are read through {@code
 * exercises_tags}, {@code logs_tags} and {@code scenarios_tags} on the test connection, which the
 * statement inspector does not rewrite. The class arms the production list.
 *
 * <p>The create endpoints ({@code POST /exercises}, {@code POST /scenarios}) resolve their write
 * tenant with {@code TenantWriteScopeResolver#tenantForWrite(ctx, null)}, which refuses any scope
 * that does not pin exactly one tenant: a header naming two tenants never reaches the tag lookup,
 * so no red is producible there. The same holds for {@code
 * ScenarioService#prepareScenarioFromScenarioInput}, reached only through {@code
 * createScenarioWithInjectorContracts}, which resolves its write tenant the same way.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("A simulation, its logs and a scenario link only the tags of their own tenant")
class SimulationScenarioTagLinkTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final String EXERCISE_TAGS =
      "SELECT tag_id FROM exercises_tags WHERE exercise_id = ?";
  private static final String LOG_TAGS = "SELECT tag_id FROM logs_tags WHERE log_id = ?";
  private static final String SCENARIO_TAGS =
      "SELECT tag_id FROM scenarios_tags WHERE scenario_id = ?";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ExerciseService exerciseService;
  @Autowired private ScenarioService scenarioService;
  @Autowired private TagRepository tagRepository;

  private String otherTenant;
  private String exercise;
  private String scenario;
  private String tagInOther;
  private String tagInDefault;

  @BeforeEach
  void seedBothTenants() throws Exception {
    // The caller belongs to both tenants, so the header can name both of them.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    otherTenant = tenantHelper.createTenantWithCurrentUser("sim-scenario-tag-o").getId();

    Exercise createdExercise = new Exercise();
    createdExercise.setName("tag-link-exercise");
    createdExercise.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    exercise = exerciseService.createExercise(createdExercise).getId();

    Scenario createdScenario = new Scenario();
    createdScenario.setName("tag-link-scenario");
    createdScenario.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    scenario = scenarioService.createScenario(createdScenario).getId();

    tagInOther = saveTag(tagRepository, otherTenant);
    tagInDefault = saveTag(tagRepository, Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Header route naming two tenants")
  class HeaderRouteNamingTwoTenants {

    @Test
    @DisplayName(
        "given a simulation of the default tenant when its information is updated with tags of"
            + " each tenant then only the tag of the default tenant is linked")
    void
        given_bodyNamingBothTenants_should_linkOnlyTheTagOfDefaultTenant_onUpdateExerciseInformation()
            throws Exception {
      // -- Arrange --
      armRequest();
      UpdateExerciseInput input = new UpdateExerciseInput();
      input.setName("tag-link-exercise-updated");
      input.setTagIds(List.of(tagInOther, tagInDefault));

      // -- Act --
      mvc.perform(
              put(EXERCISE_URI + "/{exerciseId}", exercise)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, EXERCISE_TAGS, exercise))
          .as("tags linked to the simulation: kept %s, dropped %s", tagInDefault, tagInOther)
          .containsExactly(tagInDefault);
    }

    @Test
    @DisplayName(
        "given a simulation of the default tenant when its tags are replaced with tags of each"
            + " tenant then only the tag of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheTagOfDefaultTenant_onUpdateExerciseTags()
        throws Exception {
      // -- Arrange --
      armRequest();
      ExerciseUpdateTagsInput input = new ExerciseUpdateTagsInput();
      input.setTagIds(List.of(tagInOther, tagInDefault));

      // -- Act --
      mvc.perform(
              put(EXERCISE_URI + "/{exerciseId}/tags", exercise)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, EXERCISE_TAGS, exercise))
          .as("tags linked to the simulation: kept %s, dropped %s", tagInDefault, tagInOther)
          .containsExactly(tagInDefault);
    }

    @Test
    @DisplayName(
        "given a simulation of the default tenant when a log is created with tags of each tenant"
            + " then only the tag of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheTagOfDefaultTenant_onCreateLog()
        throws Exception {
      // -- Arrange --
      armRequest();
      LogCreateInput input = logInput(List.of(tagInOther, tagInDefault));

      // -- Act --
      String response =
          mvc.perform(
                  post(EXERCISE_URI + "/{exerciseId}/logs", exercise)
                      .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- Assert --
      String logId = JsonPath.read(response, "$.log_id");
      assertThat(column(entityManager, LOG_TAGS, logId))
          .as("tags linked to the log: kept %s, dropped %s", tagInDefault, tagInOther)
          .containsExactly(tagInDefault);
    }

    @Test
    @DisplayName(
        "given a simulation of the default tenant when a log is edited with tags of each tenant"
            + " then only the tag of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheTagOfDefaultTenant_onUpdateLog()
        throws Exception {
      // -- Arrange --
      armRequest();
      String logId = createLog(List.of());

      // -- Act --
      mvc.perform(
              put(EXERCISE_URI + "/{exerciseId}/logs/{logId}", exercise, logId)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(logInput(List.of(tagInOther, tagInDefault))))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, LOG_TAGS, logId))
          .as("tags linked to the log: kept %s, dropped %s", tagInDefault, tagInOther)
          .containsExactly(tagInDefault);
    }

    @Test
    @DisplayName(
        "given a scenario of the default tenant when it is updated with tags of each tenant then"
            + " only the tag of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheTagOfDefaultTenant_onUpdateScenario()
        throws Exception {
      // -- Arrange --
      armRequest();
      UpdateScenarioInput input = new UpdateScenarioInput();
      input.setName("tag-link-scenario-updated");
      input.setTagIds(List.of(tagInOther, tagInDefault));

      // -- Act --
      mvc.perform(
              put(SCENARIO_URI + "/{scenarioId}", scenario)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, SCENARIO_TAGS, scenario))
          .as("tags linked to the scenario: kept %s, dropped %s", tagInDefault, tagInOther)
          .containsExactly(tagInDefault);
    }

    @Test
    @DisplayName(
        "given a scenario of the default tenant when its tags are replaced with tags of each"
            + " tenant then only the tag of the default tenant is linked")
    void given_bodyNamingBothTenants_should_linkOnlyTheTagOfDefaultTenant_onUpdateScenarioTags()
        throws Exception {
      // -- Arrange --
      armRequest();
      ScenarioUpdateTagsInput input = new ScenarioUpdateTagsInput();
      input.setTagIds(List.of(tagInOther, tagInDefault));

      // -- Act --
      mvc.perform(
              put(SCENARIO_URI + "/{scenarioId}/tags", scenario)
                  .header(TENANT_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + otherTenant)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, SCENARIO_TAGS, scenario))
          .as("tags linked to the scenario: kept %s, dropped %s", tagInDefault, tagInOther)
          .containsExactly(tagInDefault);
    }
  }

  @Nested
  @DisplayName("Prefixed route")
  class PrefixedRoute {

    @Test
    @DisplayName(
        "given a simulation of the default tenant when the body under its path names the tag of"
            + " the other tenant then no tag is linked")
    void given_bodyNamingTheOtherTenant_should_linkNothing() throws Exception {
      // -- Arrange --
      armRequest();
      UpdateExerciseInput input = new UpdateExerciseInput();
      input.setName("tag-link-exercise-updated");
      input.setTagIds(List.of(tagInOther));

      // -- Act --
      mvc.perform(
              put(TENANT_EXERCISE_URI + "/{exerciseId}", Tenant.DEFAULT_TENANT_UUID, exercise)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, EXERCISE_TAGS, exercise)).isEmpty();
    }

    @Test
    @DisplayName(
        "given a simulation of the default tenant when the body under its path names its own tag"
            + " then the tag is linked")
    void given_bodyNamingItsOwnTag_should_linkIt() throws Exception {
      // -- Arrange --
      armRequest();
      UpdateExerciseInput input = new UpdateExerciseInput();
      input.setName("tag-link-exercise-updated");
      input.setTagIds(List.of(tagInDefault));

      // -- Act --
      mvc.perform(
              put(TENANT_EXERCISE_URI + "/{exerciseId}", Tenant.DEFAULT_TENANT_UUID, exercise)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, EXERCISE_TAGS, exercise)).containsExactly(tagInDefault);
    }

    @Test
    @DisplayName(
        "given a scenario of the default tenant when the body under its path names the tag of"
            + " the other tenant then no tag is linked")
    void given_bodyNamingTheOtherTenant_should_linkNothing_onScenario() throws Exception {
      // -- Arrange --
      armRequest();
      UpdateScenarioInput input = new UpdateScenarioInput();
      input.setName("tag-link-scenario-updated");
      input.setTagIds(List.of(tagInOther));

      // -- Act --
      mvc.perform(
              put(TENANT_SCENARIO_URI + "/{scenarioId}", Tenant.DEFAULT_TENANT_UUID, scenario)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, SCENARIO_TAGS, scenario)).isEmpty();
    }

    @Test
    @DisplayName(
        "given a scenario of the default tenant when the body under its path names its own tag"
            + " then the tag is linked")
    void given_bodyNamingItsOwnTag_should_linkIt_onScenario() throws Exception {
      // -- Arrange --
      armRequest();
      UpdateScenarioInput input = new UpdateScenarioInput();
      input.setName("tag-link-scenario-updated");
      input.setTagIds(List.of(tagInDefault));

      // -- Act --
      mvc.perform(
              put(TENANT_SCENARIO_URI + "/{scenarioId}", Tenant.DEFAULT_TENANT_UUID, scenario)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      assertThat(column(entityManager, SCENARIO_TAGS, scenario)).containsExactly(tagInDefault);
    }
  }

  // -- Helpers --

  private LogCreateInput logInput(List<String> tagIds) {
    LogCreateInput input = new LogCreateInput();
    input.setTitle("tag-link-log");
    input.setContent("content");
    input.setTagIds(tagIds);
    return input;
  }

  private String createLog(List<String> tagIds) throws Exception {
    String response =
        mvc.perform(
                post(EXERCISE_URI + "/{exerciseId}/logs", exercise)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(logInput(tagIds)))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.log_id");
  }

  /**
   * The tag of the other tenant carries its tenant, the simulation and scenario carry the default
   * tenant, then the ambient tenant is reset to the default one the request thread of the header
   * route carries.
   */
  private void armRequest() {
    assertThat(column(entityManager, "SELECT tenant_id FROM tags WHERE tag_id = ?", tagInOther))
        .containsExactly(otherTenant);
    assertThat(
            column(
                entityManager, "SELECT tenant_id FROM exercises WHERE exercise_id = ?", exercise))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    assertThat(
            column(
                entityManager, "SELECT tenant_id FROM scenarios WHERE scenario_id = ?", scenario))
        .containsExactly(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }
}

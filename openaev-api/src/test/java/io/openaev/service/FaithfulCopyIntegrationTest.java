package io.openaev.service;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.TENANT_ATOMIC_TESTING_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.createMarkingDefinition;
import static io.openaev.utils.fixtures.MarkingDefinitionFixture.uniqueDefinition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Document;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.fixtures.composers.DocumentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.fixtures.composers.MarkingDefinitionComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
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

/**
 * Copies made by a user cleared for {@code TLP:GREEN} keep the {@code TLP:RED} target they cannot
 * see (#8726): duplicating or relaunching an atomic testing, duplicating a scenario, a simulation
 * or one of their injects, and launching a scenario. The copy carries both targets, and the
 * response still hides the RED one.
 *
 * <p>Deliberately NOT {@code @Transactional}: each request opens its own transaction, so the
 * clearance comes from the user's group through the real transaction aspect, and the copy switches
 * it inside that transaction. The tenant and its data are committed (seeded through the background
 * primitive) and deleted after each test. Targets are read back with raw JDBC, which the statement
 * inspector does not rewrite, to see what is really stored.
 */
@WithMockUser(isAdmin = false)
@DisplayName("Copies keep the targets the user is not cleared for")
class FaithfulCopyIntegrationTest extends IntegrationTest {

  private static final Set<Capability> CAPABILITIES =
      Set.of(
          Capability.ACCESS_ASSETS,
          Capability.ACCESS_ASSESSMENT,
          Capability.MANAGE_ASSESSMENT,
          Capability.LAUNCH_ASSESSMENT);

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private DataSource dataSource;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private DocumentComposer documentComposer;

  private JdbcTemplate jdbc;
  private String userId;
  private String tenantId;
  private String tagId;
  private String documentId;
  private String tlpGreen;
  private String tlpRed;
  private Endpoint green;
  private Endpoint red;

  @BeforeEach
  void seedTenantWithAGreenClearance() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    userId = testUserHolder.get().getId();
    tenantId =
        tenantHelper
            .createTenantWithCapabilities("faithful-copy-" + UUID.randomUUID(), CAPABILITIES)
            .getId();
    // Orders above the seeded 10..50 band so a fixture never ties with a default.
    inTenant(
        () -> {
          tlpGreen = marking(60);
          tlpRed = marking(70);
          green = endpoint("green");
          red = endpoint("red");
          return null;
        });
    String groupId =
        jdbc.queryForObject(
            "SELECT g.group_id FROM groups g JOIN users_groups ug ON ug.group_id = g.group_id"
                + " WHERE g.tenant_id = ? AND ug.user_id = ?",
            String.class,
            tenantId,
            userId);
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)", groupId, tlpGreen);
    clearanceCache.evictForUser(userId);
  }

  @AfterEach
  void cleanup() {
    // No ON DELETE CASCADE from the scenario reply-to addresses: removed before the tenant.
    jdbc.update(
        "DELETE FROM scenario_mails_reply_to WHERE scenario_id IN"
            + " (SELECT scenario_id FROM scenarios WHERE tenant_id = ?)",
        tenantId);
    tenantHelper.deleteCommittedTenants(tenantId);
    clearanceCache.evictForUser(userId);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Atomic testing")
  class AtomicTesting {

    @Test
    @DisplayName("given a TLP:GREEN user, should list the GREEN endpoint and not the RED one")
    void given_greenUser_should_notSeeTheRedEndpoint() throws Exception {
      // -- ARRANGE --
      String atomicTesting = seedThenMark(() -> atomicTesting().getId());

      // -- ACT --
      String response =
          mvc.perform(
                  post(TENANT_PREFIX + "/endpoints/search", tenantId)
                      .content(asJsonString(PaginationFixture.getDefault().size(200).build()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      // The premise of every copy below: the RED target is stored, and hidden from this user.
      assertThat(targetsOfInject(atomicTesting))
          .containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(response).contains(green.getId()).doesNotContain(red.getId());
    }

    @Test
    @DisplayName("given a duplicate by a TLP:GREEN user, should keep the RED target")
    void given_duplicate_should_keepTheRedTarget() throws Exception {
      // -- ARRANGE --
      String atomicTesting = seedThenMark(() -> atomicTesting().getId());

      // -- ACT --
      String response = postFor(TENANT_ATOMIC_TESTING_URI + "/{id}/duplicate", atomicTesting);

      // -- ASSERT --
      String duplicate = JsonPath.read(response, "$.inject_id");
      assertThat(duplicate).isNotEqualTo(atomicTesting);
      assertThat(targetsOfInject(duplicate)).containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(response).doesNotContain(red.getId());
    }

    @Test
    @DisplayName("given a relaunch by a TLP:GREEN user, should keep the RED target")
    void given_relaunch_should_keepTheRedTarget() throws Exception {
      // -- ARRANGE --
      String atomicTesting = seedThenMark(() -> atomicTesting().getId());

      // -- ACT --
      String response = postFor(TENANT_ATOMIC_TESTING_URI + "/{id}/relaunch", atomicTesting);

      // -- ASSERT --
      String relaunched = JsonPath.read(response, "$.inject_id");
      assertThat(targetsOfInject(relaunched)).containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(response).doesNotContain(red.getId());
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM injects WHERE inject_id = ?", Long.class, atomicTesting))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT inject_launched_by FROM injects WHERE inject_id = ?",
                  String.class,
                  relaunched))
          .isEqualTo(userId);
    }
  }

  @Nested
  @DisplayName("Scenario")
  class ScenarioCopies {

    @Test
    @DisplayName("given a scenario duplicated by a TLP:GREEN user, should keep the RED target")
    void given_duplicate_should_keepTheRedTarget() throws Exception {
      // -- ARRANGE --
      String scenario = seedThenMark(() -> taggedScenarioWithDocument().getId());

      // -- ACT --
      String response = postFor(TENANT_SCENARIO_URI + "/{id}", scenario);

      // -- ASSERT --
      String duplicate = JsonPath.read(response, "$.scenario_id");
      assertThat(duplicate).isNotEqualTo(scenario);
      assertThat(targetsOfInjects("inject_scenario", duplicate))
          .containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(response).doesNotContain(red.getId());
      // The response is serialized after the transaction: what the user sees must be loaded by
      // then.
      assertThat(JsonPath.<List<String>>read(response, "$.scenario_tags")).containsExactly(tagId);
      assertThat(JsonPath.<List<String>>read(response, "$.scenario_documents"))
          .containsExactly(documentId);
    }

    @Test
    @DisplayName("given an inject duplicated by a TLP:GREEN user, should keep the RED target")
    void given_injectDuplicate_should_keepTheRedTarget() throws Exception {
      // -- ARRANGE --
      List<String> ids = new ArrayList<>();
      seedThenMark(
          () -> {
            InjectComposer.Composer inject = injectWithContract().withTag(tagInTenant());
            ids.add(
                scenarioComposer
                    .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
                    .withInject(inject)
                    .persist()
                    .get()
                    .getId());
            ids.add(inject.get().getId());
            return null;
          });

      // -- ACT --
      String response =
          postFor(TENANT_SCENARIO_URI + "/{id}/injects/{injectId}", ids.get(0), ids.get(1));

      // -- ASSERT --
      String duplicate = JsonPath.read(response, "$.inject_id");
      assertThat(duplicate).isNotEqualTo(ids.get(1));
      assertThat(targetsOfInject(duplicate)).containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(response).doesNotContain(red.getId());
      // The response is serialized after the transaction: what the user sees must be loaded by
      // then.
      assertThat(JsonPath.<List<String>>read(response, "$.inject_assets"))
          .containsExactly(green.getId());
      assertThat(JsonPath.<List<String>>read(response, "$.inject_tags")).containsExactly(tagId);
    }

    @Test
    @DisplayName("given a scenario launched by a TLP:GREEN user, should keep the RED target")
    void given_launch_should_keepTheRedTarget() throws Exception {
      // -- ARRANGE --
      String scenario = seedThenMark(() -> taggedScenarioWithDocument().getId());

      // -- ACT --
      String response = postFor(TENANT_SCENARIO_URI + "/{id}/exercise/running", scenario);

      // -- ASSERT --
      String simulation = JsonPath.read(response, "$.exercise_id");
      assertThat(targetsOfInjects("inject_exercise", simulation))
          .containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(response).doesNotContain(red.getId());
      // The response is serialized after the transaction: what the user sees must be loaded by
      // then.
      assertThat(JsonPath.<List<String>>read(response, "$.exercise_tags")).containsExactly(tagId);
    }
  }

  @Nested
  @DisplayName("Simulation")
  class SimulationCopies {

    @Test
    @DisplayName("given a simulation duplicated by a TLP:GREEN user, should keep the RED target")
    void given_duplicate_should_keepTheRedTarget() throws Exception {
      // -- ARRANGE --
      String simulation = seedThenMark(() -> simulation().get().getId());

      // -- ACT --
      String response = postFor(TENANT_EXERCISE_URI + "/{id}", simulation);

      // -- ASSERT --
      String duplicate = JsonPath.read(response, "$.exercise_id");
      assertThat(duplicate).isNotEqualTo(simulation);
      assertThat(targetsOfInjects("inject_exercise", duplicate))
          .containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(response).doesNotContain(red.getId());
    }

    @Test
    @DisplayName("given an inject duplicated by a TLP:GREEN user, should keep the RED target")
    void given_injectDuplicate_should_keepTheRedTarget() throws Exception {
      // -- ARRANGE --
      List<String> ids = new ArrayList<>();
      seedThenMark(
          () -> {
            InjectComposer.Composer inject = injectWithContract().withTag(tagInTenant());
            ids.add(
                exerciseComposer
                    .forExercise(ExerciseFixture.createDefaultExercise())
                    .withInject(inject)
                    .persist()
                    .get()
                    .getId());
            ids.add(inject.get().getId());
            return null;
          });

      // -- ACT --
      String response =
          postFor(TENANT_EXERCISE_URI + "/{id}/injects/{injectId}", ids.get(0), ids.get(1));

      // -- ASSERT --
      String duplicate = JsonPath.read(response, "$.inject_id");
      assertThat(duplicate).isNotEqualTo(ids.get(1));
      assertThat(targetsOfInject(duplicate)).containsExactlyInAnyOrder(green.getId(), red.getId());
      assertThat(response).doesNotContain(red.getId());
      // The response is serialized after the transaction: what the user sees must be loaded by
      // then.
      assertThat(JsonPath.<List<String>>read(response, "$.inject_assets"))
          .containsExactly(green.getId());
      assertThat(JsonPath.<List<String>>read(response, "$.inject_tags")).containsExactly(tagId);
    }
  }

  // -- Helpers --

  private String postFor(String uriTemplate, Object... ids) throws Exception {
    Object[] variables = new Object[ids.length + 1];
    variables[0] = tenantId;
    System.arraycopy(ids, 0, variables, 1, ids.length);
    return mvc.perform(post(uriTemplate, variables).with(csrf()))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  /**
   * Seeds in the tenant, then marks GREEN and RED out of band: an ORM write of a marking is itself
   * guarded by the rewrite, and a later save of a detached endpoint would reset its markings.
   */
  private String seedThenMark(Supplier<String> seed) {
    String id = inTenant(seed);
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        new String[] {tlpGreen},
        green.getId());
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?", new String[] {tlpRed}, red.getId());
    return id;
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
            EndpointFixture.createEndpoint("faithful-copy-" + name + "-" + UUID.randomUUID()))
        .persist()
        .get();
  }

  /** An inject on GREEN and RED, with its own contract; not persisted yet. */
  private Inject injectTargetingGreenAndRed() {
    Inject inject = InjectFixture.getInjectWithoutContract();
    inject.setAssets(new ArrayList<>(List.of(green, red)));
    return inject;
  }

  private Document documentInTenant() {
    Document document = DocumentFixture.getDocumentJpeg();
    document.setTenant(new Tenant(tenantId));
    documentId = documentComposer.forDocument(document).persist().get().getId();
    return document;
  }

  private TagComposer.Composer tagInTenant() {
    Tag tag = TagFixture.getTagWithText("faithful-copy-" + UUID.randomUUID());
    tag.setTenant(new Tenant(tenantId));
    TagComposer.Composer composer = tagComposer.forTag(tag);
    tagId = composer.persist().get().getId();
    return composer;
  }

  private InjectComposer.Composer injectWithContract() {
    return injectComposer
        .forInject(injectTargetingGreenAndRed())
        .withInjectorContract(
            injectorContractComposer
                .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                .withInjector(
                    InjectorFixture.createDefaultInjector("faithful-copy-" + UUID.randomUUID())));
  }

  private Inject atomicTesting() {
    return injectWithContract().persist().get();
  }

  private ScenarioComposer.Composer scenario() {
    return scenarioComposer
        .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
        .withInject(injectWithContract())
        .persist();
  }

  /** A {@link #scenario()} that also carries a tag and a document of the tenant. */
  private Scenario taggedScenarioWithDocument() {
    ScenarioComposer.Composer composer =
        scenarioComposer
            .forScenario(ScenarioFixture.createDefaultIncidentResponseScenario())
            .withInject(injectWithContract());
    composer.get().setTags(new HashSet<>(Set.of(tagInTenant().get())));
    composer.get().setDocuments(new ArrayList<>(List.of(documentInTenant())));
    return composer.persist().get();
  }

  private ExerciseComposer.Composer simulation() {
    return exerciseComposer
        .forExercise(ExerciseFixture.createDefaultExercise())
        .withInject(injectWithContract())
        .persist();
  }

  private List<String> targetsOfInject(String injectId) {
    return jdbc.queryForList(
        "SELECT asset_id FROM injects_assets WHERE inject_id = ?", String.class, injectId);
  }

  /** The targets of every inject of a scenario ({@code inject_scenario}) or a simulation. */
  private List<String> targetsOfInjects(String parentColumn, String parentId) {
    return jdbc.queryForList(
        "SELECT ia.asset_id FROM injects_assets ia JOIN injects i ON i.inject_id = ia.inject_id"
            + " WHERE i."
            + parentColumn
            + " = ?",
        String.class,
        parentId);
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

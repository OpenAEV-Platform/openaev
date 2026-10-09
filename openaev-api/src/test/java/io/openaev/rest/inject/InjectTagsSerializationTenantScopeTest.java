package io.openaev.rest.inject;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.inject.InjectApi.TENANT_INJECT_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.CredentialSecretReferenceFixture.getUsernamePasswordReference;
import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultExercise;
import static io.openaev.utils.fixtures.InjectFixture.getDefaultInject;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.inject.form.InjectInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.TagComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
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
 * With {@code tags} and {@code secret_references} on v2 tenant isolation, the inject read endpoints
 * return raw {@code Inject} entities and would serialize their lazy {@code inject_tags} and {@code
 * inject_secret_references} open-in-view, AFTER the controller transaction has committed and {@code
 * app.current_tenants} is cleared: the lookup then fails closed to an empty array. The inject edit
 * form (scenario, simulation and atomic testing) is filled from {@code GET /injects/{id}}, and the
 * logical chains tab re-sends the injects listed for a simulation or a scenario, so saving erased
 * the inject's tags. {@link InjectLinksInitializer} loads them inside the scoped transaction.
 *
 * <p>The other associations an inject serializes are EAGER, or serialized as a bare id ({@code
 * inject_injector}, {@code inject_user}) without a database load. The tests run with the production
 * list of isolated tables, read from {@code application.properties}, so they follow any later
 * activation.
 *
 * <p>The class is deliberately NOT {@code @Transactional}: a rolled-back test transaction never
 * commits, so the tenant-local GUC would stay alive through serialization and MASK the very failure
 * this pins. Seeding, checks and cleanup therefore run in committed transactions opened through
 * {@link TenantScopedTransaction}, scoped to the test tenant.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Inject read endpoints serialize every lazy association")
class InjectTagsSerializationTenantScopeTest extends IntegrationTest {

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
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private TagRepository tagRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private SecretReferenceRepository secretReferenceRepository;

  private String tenant;
  private final List<String> seededScenarios = new ArrayList<>();
  private final List<String> seededSecretReferences = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    tagComposer.reset();
    injectComposer.reset();
    exerciseComposer.reset();
    seededScenarios.clear();
    seededSecretReferences.clear();
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("inject-tag-sink").getId();
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
          secretReferenceRepository.deleteAllById(seededSecretReferences);
          exerciseRepository.deleteAllById(
              exerciseComposer.generatedItems.stream().map(Exercise::getId).toList());
          scenarioRepository.deleteAllById(seededScenarios);
          tagRepository.deleteAllById(tagComposer.generatedItems.stream().map(Tag::getId).toList());
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("Get an inject")
  class GetInject {

    @Test
    @DisplayName(
        "given an inject carrying every lazy association when it is read by id then all of them"
            + " are serialized")
    void
        given_an_inject_carrying_every_lazy_association_when_it_is_read_by_id_then_all_of_them_are_serialized()
            throws Exception {
      // -- Arrange --
      Inject inject = seedSimulationInject();

      // -- Act & Assert --
      // Without the in-scope load inject_tags and inject_secret_references serialize as [] here:
      // the post-commit open-in-view load runs with app.current_tenants cleared and fails closed.
      expectEveryLazyAssociation(getInject(inject), "$", inject);
    }
  }

  @Nested
  @DisplayName("List the injects of a simulation or a scenario")
  class ListInjects {

    @Test
    @DisplayName(
        "given a simulation inject carrying every lazy association when the simulation injects are"
            + " listed then all of them are serialized")
    void
        given_a_simulation_inject_carrying_every_lazy_association_when_the_simulation_injects_are_listed_then_all_of_them_are_serialized()
            throws Exception {
      // -- Arrange --
      Inject inject = seedSimulationInject();

      // -- Act & Assert --
      ResultActions response =
          mvc.perform(
                  get(
                          TENANT_EXERCISE_URI + "/{exerciseId}/injects",
                          tenant,
                          inject.getExercise().getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$", hasSize(1)));
      expectEveryLazyAssociation(response, "$[0]", inject);
    }

    @Test
    @DisplayName(
        "given a scenario inject carrying every lazy association when the scenario injects are"
            + " listed then all of them are serialized")
    void
        given_a_scenario_inject_carrying_every_lazy_association_when_the_scenario_injects_are_listed_then_all_of_them_are_serialized()
            throws Exception {
      // -- Arrange --
      Inject inject = seedScenarioInject();

      // -- Act & Assert --
      ResultActions response =
          mvc.perform(
                  get(
                          TENANT_SCENARIO_URI + "/{scenarioId}/injects",
                          tenant,
                          inject.getScenario().getId())
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$", hasSize(1)));
      expectEveryLazyAssociation(response, "$[0]", inject);
    }
  }

  @Nested
  @DisplayName("Edit an inject")
  class EditInject {

    @Test
    @DisplayName(
        "given a tagged inject when a tag is added through the edit form then nothing existing is"
            + " lost")
    void
        given_a_tagged_inject_when_a_tag_is_added_through_the_edit_form_then_nothing_existing_is_lost()
            throws Exception {
      // -- Arrange --
      Inject inject = seedSimulationInject();
      String addedTagId = inTenantScope(() -> newTag().persist().get().getId());
      List<String> editedTags = new ArrayList<>(tagIds(inject.getTags()));
      editedTags.add(addedTagId);

      // -- Act --
      // Same flow as the edit form: it is filled from the get-by-id response, then the new tag is
      // added. The response must therefore show every existing link: an empty tag list here is
      // what erased the tags before the fix.
      expectEveryLazyAssociation(getInject(inject), "$", inject);
      mvc.perform(
              put(
                      TENANT_INJECT_URI + "/{exerciseId}/{injectId}",
                      tenant,
                      inject.getExercise().getId(),
                      inject.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(editInput(inject, editedTags)))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      List<String> secretReferenceIds = secretReferenceIds(inject);
      inTenantScope(
          () -> {
            Inject saved = injectRepository.findById(inject.getId()).orElseThrow();
            assertThat(tagIds(saved.getTags())).containsExactlyInAnyOrderElementsOf(editedTags);
            assertThat(secretReferenceIds(saved)).containsExactlyElementsOf(secretReferenceIds);
            return null;
          });
    }

    private InjectInput editInput(Inject inject, List<String> tagIds) {
      InjectInput input = new InjectInput();
      input.setTitle(inject.getTitle());
      input.setTagIds(tagIds);
      return input;
    }
  }

  // -- SEEDING --

  /** An inject of a simulation, with a tag and a secret reference: every lazy association. */
  private Inject seedSimulationInject() {
    return inTenantScope(
        () -> {
          Exercise exercise = createDefaultExercise();
          exercise.setTenant(new Tenant(tenant));
          Inject inject = newInject();
          return injectComposer
              .forInject(inject)
              .withTag(newTag())
              .withExercise(exerciseComposer.forExercise(exercise))
              .persist()
              .get();
        });
  }

  /** An inject of a scenario, with a tag and a secret reference: every lazy association. */
  private Inject seedScenarioInject() {
    return inTenantScope(
        () -> {
          Scenario scenario = createDefaultCrisisScenario();
          scenario.setTenant(new Tenant(tenant));
          seededScenarios.add(scenarioRepository.save(scenario).getId());
          Inject inject = newInject();
          inject.setScenario(scenario);
          return injectComposer.forInject(inject).withTag(newTag()).persist().get();
        });
  }

  /** Must run inside a scoped transaction: it saves the secret reference the inject points to. */
  private Inject newInject() {
    SecretReference secretReference = getUsernamePasswordReference();
    secretReference.setName("inject-secret-" + UUID.randomUUID());
    secretReference.setTenant(new Tenant(tenant));
    SecretReference savedSecretReference = secretReferenceRepository.save(secretReference);
    seededSecretReferences.add(savedSecretReference.getId());

    Inject inject = getDefaultInject();
    inject.setTitle("inject-" + UUID.randomUUID());
    inject.setTenant(new Tenant(tenant));
    inject.setSecretReferences(new ArrayList<>(List.of(savedSecretReference)));
    return inject;
  }

  private TagComposer.Composer newTag() {
    Tag tag = getTagWithText("inject-tag-" + UUID.randomUUID());
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

  private ResultActions getInject(Inject inject) throws Exception {
    return mvc.perform(
            get(TENANT_INJECT_URI + "/{injectId}", tenant, inject.getId())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk());
  }

  private static void expectEveryLazyAssociation(
      ResultActions response, String injectPath, Inject inject) throws Exception {
    response
        .andExpect(
            jsonPath(
                injectPath + ".inject_tags",
                containsInAnyOrder(tagIds(inject.getTags()).toArray())))
        .andExpect(
            jsonPath(
                injectPath + ".inject_secret_references",
                contains(secretReferenceIds(inject).toArray())));
  }

  private static List<String> tagIds(Set<Tag> tags) {
    return tags.stream().map(Tag::getId).toList();
  }

  private static List<String> secretReferenceIds(Inject inject) {
    return inject.getSecretReferences().stream().map(SecretReference::getId).toList();
  }
}

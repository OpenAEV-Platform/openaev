package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.ExerciseFixture.createDefaultExercise;
import static io.openaev.utils.fixtures.TagFixture.getTagWithText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
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
import io.openaev.database.model.Log;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.LogRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.exercise.form.LogCreateInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
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
import java.util.stream.IntStream;
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
 * With {@code tags} on v2 tenant isolation, {@code GET /exercises/{id}/logs} returns raw {@code
 * Log} entities and would serialize their lazy {@code log_tags} open-in-view, AFTER the controller
 * transaction has committed and {@code app.current_tenants} is cleared: the tag lookup then fails
 * closed to an empty array. The logs page shows the tags from this response and fills the log edit
 * form with them, so the tags vanished on reload and saving a log erased them. {@link ExerciseApi}
 * loads them inside the scoped transaction to prevent it. Tags are the only lazy association a log
 * serializes beyond bare ids ({@code log_exercise}, {@code log_user}).
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
@DisplayName("Simulation logs serialize their tags with the production isolation")
class ExerciseLogTagsSerializationTenantScopeTest extends IntegrationTest {

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
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private TagRepository tagRepository;
  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private LogRepository logRepository;

  private String tenant;
  private final List<String> seededLogs = new ArrayList<>();

  @BeforeEach
  void setUp() throws Exception {
    tagComposer.reset();
    exerciseComposer.reset();
    seededLogs.clear();
    // A tenant the current user is a member of, so the request scope covers the seeded rows.
    tenant = tenantHelper.createTenantWithCurrentUser("simulation-log-tag-sink").getId();
  }

  @AfterEach
  void cleanup() {
    // The seeded rows were committed: delete them by id in a transaction scoped to the tenant,
    // then the tenant itself. The helper's deletes on collectors and collector_types (isolated in
    // production) join this transaction, so they run with the tenant in scope.
    inTenantScope(
        () -> {
          logRepository.deleteAllById(seededLogs);
          exerciseRepository.deleteAllById(
              exerciseComposer.generatedItems.stream().map(Exercise::getId).toList());
          tagRepository.deleteAllById(tagComposer.generatedItems.stream().map(Tag::getId).toList());
          tenantHelper.deleteCommittedTenants(tenant);
          return null;
        });
  }

  @Nested
  @DisplayName("List the logs of a simulation")
  class ListLogs {

    @Test
    @DisplayName(
        "given a log carrying tags when the simulation logs are listed then the tags are"
            + " serialized")
    void
        given_a_log_carrying_tags_when_the_simulation_logs_are_listed_then_the_tags_are_serialized()
            throws Exception {
      // -- Arrange --
      Log log = seedLog(2);

      // -- Act & Assert --
      // Without the in-scope load the lazy log_tags serializes as [] here: the post-commit
      // open-in-view load runs with app.current_tenants cleared and fails closed.
      listLogs(log).andExpect(jsonPath("$[0].log_tags", containsInAnyOrder(tagIds(log).toArray())));
    }

    @Test
    @DisplayName(
        "given a log with no tag when the simulation logs are listed then its tags are genuinely"
            + " empty")
    void given_a_log_with_no_tag_when_the_simulation_logs_are_listed_then_its_tags_are_empty()
        throws Exception {
      // -- Arrange --
      Log log = seedLog(0);

      // -- Act & Assert --
      // Empty here proves the assertion above tracks the real links and is not always non-empty.
      listLogs(log).andExpect(jsonPath("$[0].log_tags", empty()));
    }
  }

  @Nested
  @DisplayName("Edit a log")
  class EditLog {

    @Test
    @DisplayName(
        "given a tagged log when a tag is added through the edit form then the existing tag is"
            + " kept")
    void
        given_a_tagged_log_when_a_tag_is_added_through_the_edit_form_then_the_existing_tag_is_kept()
            throws Exception {
      // -- Arrange --
      Log log = seedLog(1);
      String addedTagId = inTenantScope(() -> newTag().persist().get().getId());
      List<String> editedTags = new ArrayList<>(tagIds(log));
      editedTags.add(addedTagId);

      // -- Act --
      // Same flow as the logs page: the edit form is filled from the list response, then the new
      // tag is added. The list must therefore show the existing tag: an empty list here is what
      // erased it before the fix.
      listLogs(log).andExpect(jsonPath("$[0].log_tags", containsInAnyOrder(tagIds(log).toArray())));
      mvc.perform(
              put(
                      TENANT_EXERCISE_URI + "/{exerciseId}/logs/{logId}",
                      tenant,
                      log.getExercise().getId(),
                      log.getId())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(editInput(log, editedTags)))
                  .with(csrf()))
          .andExpect(status().isOk());

      // -- Assert --
      inTenantScope(
          () -> {
            Log saved = logRepository.findById(log.getId()).orElseThrow();
            assertThat(tagIds(saved)).containsExactlyInAnyOrderElementsOf(editedTags);
            return null;
          });
    }

    private LogCreateInput editInput(Log log, List<String> tagIds) {
      LogCreateInput input = new LogCreateInput();
      input.setTitle(log.getTitle());
      input.setContent(log.getContent());
      input.setTagIds(tagIds);
      return input;
    }
  }

  // -- SEEDING --

  /** A simulation holding a single log with the given number of tags. */
  private Log seedLog(int tagCount) {
    Log saved =
        inTenantScope(
            () -> {
              Exercise exercise = createDefaultExercise();
              exercise.setTenant(new Tenant(tenant));
              exerciseComposer.forExercise(exercise).persist();
              Set<Tag> tags = new HashSet<>();
              IntStream.range(0, tagCount).forEach(i -> tags.add(newTag().persist().get()));
              Log log = new Log();
              log.setTitle("log-" + UUID.randomUUID());
              log.setContent("Log content");
              log.setExercise(exercise);
              log.setTags(tags);
              return logRepository.save(log);
            });
    seededLogs.add(saved.getId());
    return saved;
  }

  private TagComposer.Composer newTag() {
    Tag tag = getTagWithText("simulation-log-tag-" + UUID.randomUUID());
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

  // -- CALLS --

  /** Lists the logs of the log's simulation: each seeded simulation holds only this log. */
  private ResultActions listLogs(Log log) throws Exception {
    return mvc.perform(
            get(TENANT_EXERCISE_URI + "/{exerciseId}/logs", tenant, log.getExercise().getId())
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)));
  }

  private static List<String> tagIds(Log log) {
    return log.getTags().stream().map(Tag::getId).toList();
  }
}

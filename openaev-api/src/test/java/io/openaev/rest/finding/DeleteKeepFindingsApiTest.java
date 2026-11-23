package io.openaev.rest.finding;

import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Finding;
import io.openaev.database.repository.FindingOccurrenceRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting a simulation, a scenario or an inject deletes the findings its injects produced by
 * default (unchanged behaviour); keep_findings=true keeps them, detached from the deleted element,
 * with their occurrences.
 */
@Transactional
@DisplayName("Delete with or without keep_findings")
class DeleteKeepFindingsApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private FindingComposer findingComposer;
  @Autowired private FindingRepository findingRepository;
  @Autowired private FindingOccurrenceRepository findingOccurrenceRepository;

  @BeforeEach
  void setUp() {
    findingComposer.reset();
    injectComposer.reset();
    exerciseComposer.reset();
    scenarioComposer.reset();
  }

  private record Seeded(String ownerId, String injectId, String findingId) {}

  private Seeded simulationWithFinding() {
    FindingComposer.Composer finding =
        findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
    InjectComposer.Composer inject =
        injectComposer.forInject(InjectFixture.getDefaultInject()).withFinding(finding);
    ExerciseComposer.Composer simulation =
        exerciseComposer
            .forExercise(ExerciseFixture.createFinishedAttackExercise())
            .withInject(inject)
            .persist();
    flushAndClear();
    return new Seeded(simulation.get().getId(), inject.get().getId(), finding.get().getId());
  }

  private Seeded scenarioWithFinding() {
    FindingComposer.Composer finding =
        findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
    InjectComposer.Composer inject =
        injectComposer.forInject(InjectFixture.getDefaultInject()).withFinding(finding);
    ScenarioComposer.Composer scenario =
        scenarioComposer.forScenario(ScenarioFixture.getScenario()).withInject(inject).persist();
    flushAndClear();
    return new Seeded(scenario.get().getId(), inject.get().getId(), finding.get().getId());
  }

  private Seeded atomicTestingWithFinding() {
    FindingComposer.Composer finding =
        findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
    InjectComposer.Composer atomicTesting =
        injectComposer.forInject(InjectFixture.getDefaultInject()).withFinding(finding).persist();
    flushAndClear();
    String injectId = atomicTesting.get().getId();
    return new Seeded(injectId, injectId, finding.get().getId());
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private void assertDeleted(Seeded seeded) {
    flushAndClear();
    assertThat(findingRepository.findById(seeded.findingId())).isEmpty();
    assertThat(
            findingOccurrenceRepository.findByFindingIdAndInjectId(
                seeded.findingId(), seeded.injectId()))
        .isEmpty();
  }

  private void assertKeptAndDetached(Seeded seeded) {
    flushAndClear();
    Optional<Finding> kept = findingRepository.findById(seeded.findingId());
    assertThat(kept).isPresent();
    assertThat(kept.get().getInject()).isNull();
    // The occurrence stays: its inject link is set to NULL by finding_occurrence_inject_id
    assertThat(
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM finding_occurrences"
                        + " WHERE finding_occurrence_finding_id = :findingId"
                        + " AND finding_occurrence_inject_id IS NULL")
                .setParameter("findingId", seeded.findingId())
                .getSingleResult())
        .isEqualTo(1L);
  }

  @Nested
  @DisplayName("Simulation")
  class SimulationDelete {

    @Test
    @DisplayName("Default delete removes the findings, as before")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_simulationWithFinding_should_deleteFindingsByDefault() throws Exception {
      Seeded seeded = simulationWithFinding();

      mvc.perform(delete(EXERCISE_URI + "/" + seeded.ownerId()).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      assertDeleted(seeded);
    }

    @Test
    @DisplayName("keep_findings=true keeps the findings, detached, with their occurrences")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_simulationWithFinding_and_keepFindings_should_keepThem() throws Exception {
      Seeded seeded = simulationWithFinding();

      mvc.perform(
              delete(EXERCISE_URI + "/" + seeded.ownerId())
                  .param("keep_findings", "true")
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      assertKeptAndDetached(seeded);
    }
  }

  @Nested
  @DisplayName("Scenario")
  class ScenarioDelete {

    @Test
    @DisplayName("Default delete removes the findings, as before")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_scenarioWithFinding_should_deleteFindingsByDefault() throws Exception {
      Seeded seeded = scenarioWithFinding();

      mvc.perform(delete(SCENARIO_URI + "/" + seeded.ownerId()).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      assertDeleted(seeded);
    }

    @Test
    @DisplayName("keep_findings=true keeps the findings, detached, with their occurrences")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_scenarioWithFinding_and_keepFindings_should_keepThem() throws Exception {
      Seeded seeded = scenarioWithFinding();

      mvc.perform(
              delete(SCENARIO_URI + "/" + seeded.ownerId())
                  .param("keep_findings", "true")
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      assertKeptAndDetached(seeded);
    }
  }

  @Nested
  @DisplayName("Inject of a simulation (JPA delete path)")
  class InjectDelete {

    @Test
    @DisplayName("Default delete removes the findings, as before")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_injectWithFinding_should_deleteFindingsByDefault() throws Exception {
      Seeded seeded = simulationWithFinding();

      mvc.perform(
              delete(EXERCISE_URI + "/" + seeded.ownerId() + "/injects/" + seeded.injectId())
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      assertDeleted(seeded);
    }

    @Test
    @DisplayName("keep_findings=true keeps the findings, detached, with their occurrences")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_injectWithFinding_and_keepFindings_should_keepThem() throws Exception {
      Seeded seeded = simulationWithFinding();

      mvc.perform(
              delete(EXERCISE_URI + "/" + seeded.ownerId() + "/injects/" + seeded.injectId())
                  .param("keep_findings", "true")
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      assertKeptAndDetached(seeded);
    }
  }

  @Nested
  @DisplayName("Atomic testing")
  class AtomicTestingDelete {

    @Test
    @DisplayName("Default delete removes the findings, as before")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_atomicTestingWithFinding_should_deleteFindingsByDefault() throws Exception {
      Seeded seeded = atomicTestingWithFinding();

      mvc.perform(delete(ATOMIC_TESTING_URI + "/" + seeded.injectId()).with(csrf()))
          .andExpect(status().is2xxSuccessful());

      assertDeleted(seeded);
    }

    @Test
    @DisplayName("keep_findings=true keeps the findings, detached, with their occurrences")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_atomicTestingWithFinding_and_keepFindings_should_keepThem() throws Exception {
      Seeded seeded = atomicTestingWithFinding();

      mvc.perform(
              delete(ATOMIC_TESTING_URI + "/" + seeded.injectId())
                  .param("keep_findings", "true")
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      assertKeptAndDetached(seeded);
    }

    @Test
    @DisplayName("Bulk delete removes the findings by default, as before")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_atomicTestingWithFinding_should_bulkDeleteFindingsByDefault() throws Exception {
      Seeded seeded = atomicTestingWithFinding();

      mvc.perform(
              delete(ATOMIC_TESTING_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(Map.of("inject_ids_to_process", List.of(seeded.injectId()))))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      assertDeleted(seeded);
    }

    @Test
    @DisplayName("Bulk delete with keep_findings=true keeps the findings, detached")
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    void given_atomicTestingWithFinding_and_keepFindings_should_bulkKeepThem() throws Exception {
      Seeded seeded = atomicTestingWithFinding();

      mvc.perform(
              delete(ATOMIC_TESTING_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          Map.of(
                              "inject_ids_to_process",
                              List.of(seeded.injectId()),
                              "keep_findings",
                              true)))
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful());

      assertKeptAndDetached(seeded);
    }
  }
}

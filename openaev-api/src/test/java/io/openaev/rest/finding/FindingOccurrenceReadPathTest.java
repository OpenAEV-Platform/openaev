package io.openaev.rest.finding;

import static io.openaev.rest.finding.FindingApi.FINDING_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.FindingFixture.TEXT_FIELD;
import static io.openaev.utils.fixtures.FindingOccurrenceFixture.OTHER_FIELD;
import static io.openaev.utils.fixtures.FindingOccurrenceFixture.createOccurrence;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Finding;
import io.openaev.database.model.Inject;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.specification.FindingSpecification;
import io.openaev.rest.finding.form.FindingOutput;
import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.FindingFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.FindingComposer;
import io.openaev.utils.fixtures.composers.FindingOccurrenceComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.mapper.FindingMapper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The readers reach a finding's injects, simulations and fields through its occurrences (#7848,
 * chunk 2). A finding with several occurrences is still one finding.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Finding readers going through the occurrences")
class FindingOccurrenceReadPathTest extends IntegrationTest {

  @Autowired private FindingComposer findingComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private FindingRepository findingRepository;
  @Autowired private FindingOccurrenceComposer findingOccurrenceComposer;
  @Autowired private FindingMapper findingMapper;
  @Autowired private EntityManager entityManager;
  @Autowired private MockMvc mvc;

  private Exercise simulation;
  private Inject firstInject;
  private Inject secondInject;
  private String findingId;

  @BeforeEach
  void seedAFindingOccurringInTwoInjectsOfOneSimulation() {
    findingComposer.reset();
    findingOccurrenceComposer.reset();
    injectComposer.reset();
    exerciseComposer.reset();

    FindingComposer.Composer findingWrapper =
        findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
    InjectComposer.Composer firstInjectWrapper =
        injectComposer.forInject(InjectFixture.getDefaultInject()).withFinding(findingWrapper);
    InjectComposer.Composer secondInjectWrapper =
        injectComposer.forInject(InjectFixture.getDefaultInject());
    simulation =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withInject(firstInjectWrapper)
            .withInject(secondInjectWrapper)
            .persist()
            .get();
    firstInject = firstInjectWrapper.get();
    secondInject = secondInjectWrapper.get();
    findingId = findingWrapper.get().getId();
  }

  @Nested
  @DisplayName("When a finding occurs in two injects of one simulation")
  class WhenAFindingOccursInTwoInjects {

    @BeforeEach
    void addASecondOccurrence() {
      addOccurrence(secondInject, later(), OTHER_FIELD);
    }

    @Test
    @DisplayName("Should be found once in the simulation")
    void given_twoOccurrencesInOneSimulation_should_beFoundOnce() {
      // Act
      List<Finding> findings =
          findingRepository.findAll(
              FindingSpecification.findFindingsForSimulation(simulation.getId()));

      // Assert
      assertThat(findings).extracting(Finding::getId).containsOnlyOnce(findingId);
    }

    @Test
    @DisplayName("Should be found in each inject")
    void given_twoOccurrences_should_beFoundInEachInject() {
      // Act
      List<Finding> inFirst =
          findingRepository.findAll(
              FindingSpecification.findFindingsForInject(firstInject.getId()));
      List<Finding> inSecond =
          findingRepository.findAll(
              FindingSpecification.findFindingsForInject(secondInject.getId()));

      // Assert
      assertThat(inFirst).extracting(Finding::getId).containsOnlyOnce(findingId);
      assertThat(inSecond).extracting(Finding::getId).containsOnlyOnce(findingId);
    }

    @Test
    @DisplayName("Should list both injects and both fields")
    void given_twoOccurrences_should_listBothInjectsAndFields() {
      // Act
      Finding finding = reload();

      // Assert
      assertThat(finding.getInjects())
          .extracting(Inject::getId)
          .containsExactlyInAnyOrder(firstInject.getId(), secondInject.getId());
      assertThat(finding.getSimulations()).extracting(Exercise::getId).contains(simulation.getId());
      assertThat(finding.getFields()).containsExactlyInAnyOrder(TEXT_FIELD, OTHER_FIELD);
    }

    @Test
    @DisplayName("Should show the inject and field of the latest occurrence")
    void given_twoOccurrences_should_showTheLatestOccurrence() {
      // Act
      FindingOutput output = findingMapper.toFindingOutput(reload());

      // Assert
      assertThat(output.getInjectId()).isEqualTo(secondInject.getId());
      assertThat(output.getField()).isEqualTo(OTHER_FIELD);
    }

    @Test
    @DisplayName("Should come back once when filtered on its simulation")
    void given_twoOccurrencesInTheFilteredSimulation_should_comeBackOnce() throws Exception {
      // Arrange
      SearchPaginationInput input =
          PaginationFixture.simpleSearchWithAndOperator(
              "finding_simulation", simulation.getId(), Filters.FilterOperator.eq);

      // Act & Assert
      search(input)
          .andExpect(jsonPath("$.totalElements").value(1))
          .andExpect(jsonPath("$.content[0].finding_id").value(findingId));
    }

    @Test
    @DisplayName("Should come back once when its two fields match the text search")
    void given_twoFieldsMatchingTheTextSearch_should_comeBackOnce() throws Exception {
      // Arrange: both fields contain "_field"
      SearchPaginationInput input =
          PaginationFixture.simpleSearchWithAndOperator(
              "finding_simulation", simulation.getId(), Filters.FilterOperator.eq);
      input.setTextSearch("_field");

      // Act & Assert
      search(input)
          .andExpect(jsonPath("$.totalElements").value(1))
          .andExpect(jsonPath("$.content[0].finding_id").value(findingId));
    }
  }

  @Nested
  @DisplayName("When the readers sort the findings")
  class WhenSorting {

    @Test
    @DisplayName("Should not offer the injects nor the fields as a sort: they are lists")
    void given_listProperties_should_notBeSortable() {
      // Act
      List<String> sortable =
          SchemaUtils.getSortableProperties(SchemaUtils.schema(Finding.class)).stream()
              .map(PropertySchema::getJsonName)
              .toList();

      // Assert
      assertThat(sortable).doesNotContain("finding_inject_id", "finding_field");
    }
  }

  @Nested
  @DisplayName("When the inject of the latest occurrence was deleted")
  class WhenTheLatestInjectWasDeleted {

    @Test
    @DisplayName("Should show the latest occurrence that still has its inject")
    void given_aLatestOccurrenceWithoutInject_should_showThePreviousOne() {
      // Arrange
      addOccurrence(null, later(), OTHER_FIELD);

      // Act
      FindingOutput output = findingMapper.toFindingOutput(reload());

      // Assert
      assertThat(output.getInjectId()).isEqualTo(firstInject.getId());
      assertThat(output.getField()).isEqualTo(TEXT_FIELD);
    }
  }

  // -- PRIVATE --

  private void addOccurrence(Inject inject, Instant date, String field) {
    Finding finding = findingRepository.findById(findingId).orElseThrow();
    findingOccurrenceComposer
        .forOccurrence(createOccurrence(finding, inject, date, field))
        .persist();
  }

  private ResultActions search(SearchPaginationInput input) throws Exception {
    entityManager.flush();
    entityManager.clear();
    return mvc.perform(
            post(FINDING_URI + "/search")
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  private static Instant later() {
    return Instant.now().plus(1, ChronoUnit.HOURS);
  }

  private Finding reload() {
    entityManager.flush();
    entityManager.clear();
    return findingRepository.findById(findingId).orElseThrow();
  }
}

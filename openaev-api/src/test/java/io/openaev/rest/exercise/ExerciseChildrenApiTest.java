package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Comcheck;
import io.openaev.database.model.Document;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.Log;
import io.openaev.database.repository.ComcheckRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.LogRepository;
import io.openaev.utils.fixtures.ComcheckFixture;
import io.openaev.utils.fixtures.DocumentFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.LogFixture;
import io.openaev.utils.fixtures.composers.DocumentComposer;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Simulation logs, comchecks and documents API")
class ExerciseChildrenApiTest extends IntegrationTest {

  private static final String LOG_BODY =
      "{\"log_title\":\"changed\",\"log_content\":\"changed\",\"log_tags\":[]}";

  @Autowired private MockMvc mvc;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private LogRepository logRepository;
  @Autowired private ComcheckRepository comcheckRepository;
  @Autowired private DocumentRepository documentRepository;

  private String simulationId;
  private String logId;
  private String comcheckId;
  private String documentId;
  private String otherLogId;
  private String otherComcheckId;
  private String unlinkedDocumentId;

  @BeforeEach
  void setUp() {
    exerciseComposer.reset();
    documentComposer.reset();

    DocumentComposer.Composer document =
        documentComposer.forDocument(DocumentFixture.getDocumentJpeg());
    Exercise simulation =
        exerciseComposer
            .forExercise(ExerciseFixture.createDefaultExercise())
            .withDocument(document)
            .persist()
            .get();
    Exercise otherSimulation =
        exerciseComposer.forExercise(ExerciseFixture.createDefaultExercise()).persist().get();
    Document unlinkedDocument =
        documentComposer.forDocument(DocumentFixture.getDocumentJpeg()).persist().get();
    Log log = logRepository.save(LogFixture.createLog(simulation, testUserHolder.get()));
    Log otherLog = logRepository.save(LogFixture.createLog(otherSimulation, testUserHolder.get()));
    Comcheck comcheck = comcheckRepository.save(ComcheckFixture.createComcheck(simulation));
    Comcheck otherComcheck =
        comcheckRepository.save(ComcheckFixture.createComcheck(otherSimulation));
    entityManager.flush();
    entityManager.clear();

    simulationId = simulation.getId();
    logId = log.getId();
    comcheckId = comcheck.getId();
    documentId = document.get().getId();
    otherLogId = otherLog.getId();
    otherComcheckId = otherComcheck.getId();
    unlinkedDocumentId = unlinkedDocument.getId();
  }

  private String simulationUri() {
    return EXERCISE_URI + "/" + simulationId;
  }

  private static MockHttpServletRequestBuilder withBody(
      MockHttpServletRequestBuilder request, String body) {
    return request.contentType(MediaType.APPLICATION_JSON).content(body);
  }

  /** The rows outside the simulation the calls could reach: other log, comcheck, document. */
  private String rowsOutsideTheSimulation() {
    entityManager.flush();
    entityManager.clear();
    return logRepository.findById(otherLogId).map(Log::getTitle).orElse("no log")
        + "|"
        + comcheckRepository.findById(otherComcheckId).isPresent()
        + "|"
        + documentRepository.findById(unlinkedDocumentId).isPresent();
  }

  @Nested
  @TestInstance(PER_CLASS)
  @DisplayName("Child outside the simulation")
  class ChildOutsideTheSimulation {

    Stream<Arguments> callsOnAChildOutsideTheSimulation() {
      return Stream.of(
          call(
              "update a log of another simulation",
              () -> withBody(put(simulationUri() + "/logs/" + otherLogId), LOG_BODY)),
          call(
              "delete a log of another simulation",
              () -> delete(simulationUri() + "/logs/" + otherLogId)),
          call(
              "delete a comcheck of another simulation",
              () -> delete(simulationUri() + "/comchecks/" + otherComcheckId)),
          call(
              "remove a document not linked to the simulation",
              () -> delete(simulationUri() + "/" + unlinkedDocumentId)));
    }

    private static Arguments call(String name, Supplier<MockHttpServletRequestBuilder> request) {
      return Arguments.of(name, request);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("callsOnAChildOutsideTheSimulation")
    @DisplayName("Given a child outside the simulation, should answer not found and change nothing")
    void given_childOutsideTheSimulation_should_answerNotFoundAndChangeNothing(
        String name, Supplier<MockHttpServletRequestBuilder> request) throws Exception {
      // Arrange
      String before = rowsOutsideTheSimulation();

      // Act
      mvc.perform(request.get().with(csrf()))
          // Assert
          .andExpect(status().isNotFound());
      assertThat(rowsOutsideTheSimulation()).isEqualTo(before);
    }
  }

  @Nested
  @DisplayName("Child of the simulation")
  class ChildOfTheSimulation {

    @Test
    @DisplayName("Given its own log, should update it")
    void given_ownLog_should_updateIt() throws Exception {
      // Act
      mvc.perform(withBody(put(simulationUri() + "/logs/" + logId), LOG_BODY).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();
      assertThat(logRepository.findById(logId).orElseThrow().getTitle()).isEqualTo("changed");
    }

    @Test
    @DisplayName("Given its own log, comcheck and document, should delete them")
    void given_ownLogComcheckAndDocument_should_deleteThem() throws Exception {
      // Act
      mvc.perform(delete(simulationUri() + "/logs/" + logId).with(csrf()))
          // Assert
          .andExpect(status().isOk());
      mvc.perform(delete(simulationUri() + "/comchecks/" + comcheckId).with(csrf()))
          .andExpect(status().isOk());
      mvc.perform(delete(simulationUri() + "/" + documentId).with(csrf()))
          .andExpect(status().isOk());
      entityManager.flush();
      entityManager.clear();
      assertThat(logRepository.findById(logId)).isEmpty();
      assertThat(comcheckRepository.findById(comcheckId)).isEmpty();
      assertThat(documentRepository.findById(documentId)).isEmpty();
    }
  }
}

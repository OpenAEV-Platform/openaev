package io.openaev.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Pause;
import io.openaev.database.repository.PauseRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PauseSimulationServiceTest {

  @Mock private PauseRepository pauseRepository;

  @InjectMocks private PauseSimulationService pauseSimulationService;

  /* ============================================================
   * deleteAll
   * ============================================================ */
  @Nested
  class DeleteAllTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("deleteAllProvider")
    void deleteAll_parameterized(String description, List<Pause> input) {
      pauseSimulationService.deleteAll(input);

      verify(pauseRepository).deleteAll(input);
    }

    private static Stream<Arguments> deleteAllProvider() {
      Pause p1 = mock(Pause.class);
      Pause p2 = mock(Pause.class);
      return Stream.of(
          Arguments.of("Empty list", List.of()),
          Arguments.of("Single pause", List.of(p1)),
          Arguments.of("Multiple pauses", List.of(p1, p2)));
    }
  }

  /* ============================================================
   * findAllForSimulation
   * ============================================================ */
  @Nested
  class FindAllForSimulationTests {

    @Test
    void shouldReturnRepositoryResult() {
      String simulationId = "sim-123";
      Pause p1 = mock(Pause.class);
      Pause p2 = mock(Pause.class);
      List<Pause> pauses = List.of(p1, p2);

      when(pauseRepository.findAllForExercise(simulationId)).thenReturn(pauses);

      List<Pause> result = pauseSimulationService.findAllForSimulation(simulationId);

      assertEquals(pauses, result);
      verify(pauseRepository).findAllForExercise(simulationId);
    }

    @Test
    void shouldReturnEmptyListWhenRepositoryReturnsEmpty() {
      String simulationId = "sim-123";
      when(pauseRepository.findAllForExercise(simulationId)).thenReturn(List.of());

      List<Pause> result = pauseSimulationService.findAllForSimulation(simulationId);

      assertTrue(result.isEmpty());
      verify(pauseRepository).findAllForExercise(simulationId);
    }
  }

  /* ============================================================
   * endPauseBySimulation
   * ============================================================ */
  @Nested
  class EndPauseBySimulationTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("endPauseProvider")
    void endPauseBySimulation_parameterized(String description, Instant lastPause) {
      Exercise simulation = mock(Exercise.class);

      pauseSimulationService.endPauseBySimulation(lastPause, simulation);

      ArgumentCaptor<Pause> captor = ArgumentCaptor.forClass(Pause.class);
      verify(pauseRepository).save(captor.capture());

      Pause savedPause = captor.getValue();
      assertEquals(lastPause, savedPause.getDate());
      assertEquals(simulation, savedPause.getExercise());

      long duration = savedPause.getDuration().orElseThrow();
      assertTrue(duration >= 0, "Duration should be non-negative for scenario: " + description);

      // Optional: also allow small delta to account for execution time
      long expectedMax = Duration.between(lastPause, Instant.now().plusSeconds(1)).getSeconds();
      assertTrue(duration <= expectedMax, "Duration is too large for scenario: " + description);
    }

    private static Stream<Arguments> endPauseProvider() {
      Instant now = Instant.now();
      return Stream.of(
          Arguments.of("Pause 1 minute ago", now.minusSeconds(60)),
          Arguments.of("Pause 1 hour ago", now.minusSeconds(3600)),
          Arguments.of("Pause just now", now));
    }
  }
}

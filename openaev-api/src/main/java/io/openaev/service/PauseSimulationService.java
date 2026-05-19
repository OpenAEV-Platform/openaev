package io.openaev.service;

import static java.time.Duration.between;
import static java.time.Instant.now;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.Pause;
import io.openaev.database.repository.PauseRepository;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@RequiredArgsConstructor
@Validated
@Service
public class PauseSimulationService {
  private final PauseRepository pauseRepository;

  /**
   * Deletes all provided pause records from the repository.
   *
   * @param pausesOfSimulation the list of pauses to delete
   */
  public void deleteAll(List<Pause> pausesOfSimulation) {
    pauseRepository.deleteAll(pausesOfSimulation);
  }

  /**
   * Retrieves all pause records associated with a specific simulation.
   *
   * @param simulationId the ID of the simulation to find pauses for
   * @return a list of all pauses for the specified simulation
   */
  public List<Pause> findAllForSimulation(String simulationId) {
    return pauseRepository.findAllForExercise(simulationId);
  }

  /**
   * Ends a pause period for a simulation by creating and saving a pause record.
   *
   * <p>The pause duration is calculated as the time elapsed between the pause start time and now.
   *
   * @param lastPause the instant when the pause started
   * @param simulation the simulation that was paused
   */
  public void endPauseBySimulation(@NotNull Instant lastPause, Exercise simulation) {
    Pause pause = new Pause();
    pause.setDate(lastPause);
    pause.setExercise(simulation);
    pause.setDuration(between(lastPause, now()).getSeconds());
    pauseRepository.save(pause);
  }
}

package io.openaev.rest.inject.service;

import static io.openaev.database.specification.CommunicationSpecification.fromInject;
import static io.openaev.helper.StreamHelper.fromIterable;
import static java.time.Instant.now;

import io.openaev.database.model.*;
import io.openaev.database.repository.CommunicationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.inject.form.InjectTeamsInput;
import io.openaev.rest.inject.form.InjectUpdateActivationInput;
import io.openaev.rest.inject.form.InjectUpdateStatusInput;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class SimulationInjectService {

  private final ExerciseService exerciseService;
  private final InjectService injectService;
  private final InjectStatusService injectStatusService;
  private final InjectRepository injectRepository;
  private final CommunicationRepository communicationRepository;
  private final TeamRepository teamRepository;

  // -- READ --

  /** Finds an inject that belongs to the given simulation. */
  public Inject findInjectForSimulation(
      @NotBlank final String simulationId, @NotBlank final String injectId) {
    return injectRepository
        .findByIdAndExerciseId(injectId, simulationId)
        .orElseThrow(
            () ->
                new ElementNotFoundException(
                    "Inject not found with id: " + injectId + " in simulation: " + simulationId));
  }

  /** Returns the teams assigned to an inject that belongs to the given simulation. */
  @Transactional(readOnly = true)
  public List<Team> findInjectTeamsForSimulation(
      @NotBlank final String simulationId, @NotBlank final String injectId) {
    return findInjectForSimulation(simulationId, injectId).getTeams();
  }

  /**
   * Returns and acknowledges all communications for an inject that belongs to the given simulation.
   */
  public Iterable<Communication> findAndAckCommunicationsForSimulation(
      @NotBlank final String simulationId, @NotBlank final String injectId) {
    checkInjectForSimulation(simulationId, injectId);
    List<Communication> coms =
        communicationRepository.findAll(
            fromInject(injectId), Sort.by(Sort.Direction.DESC, "receivedAt"));
    coms.forEach(com -> com.setAck(true));
    return communicationRepository.saveAll(coms);
  }

  // -- UPDATE --

  /** Toggles the activation of an inject that belongs to the given simulation. */
  public Inject updateInjectActivationForSimulation(
      @NotBlank final String simulationId,
      @NotBlank final String injectId,
      InjectUpdateActivationInput input) {
    checkInjectForSimulation(simulationId, injectId);
    return injectService.updateInjectActivation(injectId, input);
  }

  /** Triggers immediate execution of an inject that belongs to the given simulation. */
  public Inject triggerInjectForSimulation(
      @NotBlank final String simulationId, @NotBlank final String injectId) {
    Inject inject = findInjectForSimulation(simulationId, injectId);
    inject.setTriggerNowDate(now());
    inject.setUpdatedAt(now());
    return injectRepository.save(inject);
  }

  /** Sets the status of an inject that belongs to the given simulation. */
  public Inject setInjectStatusForSimulation(
      @NotBlank final String simulationId,
      @NotBlank final String injectId,
      InjectUpdateStatusInput input) {
    checkInjectForSimulation(simulationId, injectId);
    return injectStatusService.updateInjectStatus(injectId, input);
  }

  /** Replaces the teams assigned to an inject that belongs to the given simulation. */
  public Inject updateInjectTeamsForSimulation(
      @NotBlank final String simulationId,
      @NotBlank final String injectId,
      InjectTeamsInput input) {
    Inject inject = findInjectForSimulation(simulationId, injectId);
    Iterable<Team> injectTeams = teamRepository.findAllById(input.getTeamIds());
    inject.setTeams(fromIterable(injectTeams));
    return injectRepository.save(inject);
  }

  // -- DELETE --

  /** Deletes an inject that belongs to the given simulation. */
  public void deleteInject(@NotBlank final String simulationId, @NotBlank final String injectId) {
    checkInjectForSimulation(simulationId, injectId);
    Exercise exercise = this.exerciseService.exercise(simulationId);
    injectService.delete(injectId);
    this.exerciseService.updateExercise(exercise);
  }

  // -- PRIVATE --

  /** Checks that an inject belongs to the given simulation. Throws if not found. */
  private void checkInjectForSimulation(
      @NotBlank final String simulationId, @NotBlank final String injectId) {
    if (!injectRepository.existsByIdAndExerciseId(injectId, simulationId)) {
      throw new ElementNotFoundException(
          "Inject not found with id: " + injectId + " in simulation: " + simulationId);
    }
  }
}

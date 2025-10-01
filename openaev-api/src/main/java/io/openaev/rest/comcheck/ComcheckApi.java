package io.openaev.rest.comcheck;

import static io.openaev.helper.StreamHelper.fromIterable;
import static java.time.Instant.now;

import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.repository.ComcheckRepository;
import io.openaev.database.repository.ComcheckStatusRepository;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.TeamRepository;
import io.openaev.rest.comcheck.form.ComcheckInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
public class ComcheckApi extends RestBehavior {

  private ComcheckRepository comcheckRepository;
  private TeamRepository teamRepository;
  private ExerciseRepository exerciseRepository;
  private ComcheckStatusRepository comcheckStatusRepository;

  @Autowired
  public void setComcheckStatusRepository(ComcheckStatusRepository comcheckStatusRepository) {
    this.comcheckStatusRepository = comcheckStatusRepository;
  }

  @Autowired
  public void setComcheckRepository(ComcheckRepository comcheckRepository) {
    this.comcheckRepository = comcheckRepository;
  }

  @Autowired
  public void setTeamRepository(TeamRepository teamRepository) {
    this.teamRepository = teamRepository;
  }

  @Autowired
  public void setExerciseRepository(ExerciseRepository exerciseRepository) {
    this.exerciseRepository = exerciseRepository;
  }

  @GetMapping("/api/comcheck/{comcheckStatusId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public ComcheckStatus checkValidation(@PathVariable String comcheckStatusId) {
    ComcheckStatus comcheckStatus =
        comcheckStatusRepository
            .findById(comcheckStatusId)
            .orElseThrow(ElementNotFoundException::new);
    Comcheck comcheck = comcheckStatus.getComcheck();
    if (!comcheck.getState().equals(Comcheck.COMCHECK_STATUS.RUNNING)) {
      throw new UnsupportedOperationException("This comcheck is closed.");
    }
    comcheckStatus.setReceiveDate(now());
    ComcheckStatus status = comcheckStatusRepository.save(comcheckStatus);
    boolean finishedComcheck =
        comcheck.getComcheckStatus().stream()
            .noneMatch(st -> st.getState().equals(ComcheckStatus.CHECK_STATUS.RUNNING));
    if (finishedComcheck) {
      comcheck.setState(Comcheck.COMCHECK_STATUS.FINISHED);
      comcheckRepository.save(comcheck);
    }
    return status;
  }

  @DeleteMapping("/api/exercises/{exerciseId}/comchecks/{comcheckId}")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public void deleteComcheck(@PathVariable String exerciseId, @PathVariable String comcheckId) {
    comcheckRepository.deleteById(comcheckId);
  }

  @PostMapping("/api/exercises/{exerciseId}/comchecks")
  @RBAC(
      resourceId = "#exerciseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @Transactional(rollbackOn = Exception.class)
  public Comcheck communicationCheck(
      @PathVariable String exerciseId, @Valid @RequestBody ComcheckInput comCheck) {
    // 01. Create the comcheck and get the ID
    Comcheck check = new Comcheck();
    check.setUpdateAttributes(comCheck);
    check.setName(comCheck.getName());
    check.setStart(now());
    Exercise exercise =
        exerciseRepository.findById(exerciseId).orElseThrow(ElementNotFoundException::new);
    check.setExercise(exercise);
    // 02. Get users
    List<String> teamIds = comCheck.getTeamIds();
    List<Team> teams =
        teamIds.isEmpty() ? exercise.getTeams() : fromIterable(teamRepository.findAllById(teamIds));
    List<User> users = teams.stream().flatMap(team -> team.getUsers().stream()).distinct().toList();
    List<ComcheckStatus> comcheckStatuses =
        users.stream()
            .map(
                user -> {
                  ComcheckStatus comcheckStatus = new ComcheckStatus(user);
                  comcheckStatus.setComcheck(check);
                  return comcheckStatus;
                })
            .toList();
    check.setComcheckStatus(comcheckStatuses);
    return comcheckRepository.save(check);
  }
}

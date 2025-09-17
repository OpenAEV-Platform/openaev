package io.openbas.rest.security;

import static io.openbas.database.model.User.ROLE_ADMIN;

import io.openbas.config.OpenBASPrincipal;
import io.openbas.database.model.Exercise;
import io.openbas.database.model.User;
import io.openbas.database.repository.ExerciseRepository;
import io.openbas.database.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityExpression extends SecurityExpressionRoot
    implements MethodSecurityExpressionOperations {

  private final UserRepository userRepository;
  private final ExerciseRepository exerciseRepository;

  private Object filterObject;
  private Object returnObject;

  // region utils
  public SecurityExpression(
      Authentication authentication,
      final UserRepository userRepository,
      final ExerciseRepository exerciseRepository) {
    super(authentication);
    this.exerciseRepository = exerciseRepository;
    this.userRepository = userRepository;
  }

  private OpenBASPrincipal getUser() {
    return (OpenBASPrincipal) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  }

  public boolean isAdmin() {
    return isUserHasBypass();
  }

  private boolean isUserHasBypass() {
    OpenBASPrincipal principal = getUser();
    return principal != null
        && principal.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(s -> s.equals(ROLE_ADMIN));
  }

  // endregion

  // region exercise annotations

  /**
   * Check that a user is a planner for a given exercise
   *
   * @deprecated use isSimulationPlanner instead
   * @param exerciseId the exercice to search
   * @return true if the user is a planner for given exercise
   */
  @Deprecated(since = "1.11.0", forRemoval = true)
  public boolean isExercisePlanner(String exerciseId) {
    return isSimulationPlanner(exerciseId);
  }

  /**
   * Check that a user is a planner for a given simulation
   *
   * @param simulationId the simulation to check
   * @return true if the user is a planner for given simulation
   */
  public boolean isSimulationPlanner(String simulationId) {
    if (isUserHasBypass()) {
      return true;
    }
    Exercise exercise = exerciseRepository.findById(simulationId).orElseThrow();
    List<User> planners = exercise.getPlanners();
    Optional<User> planner =
        planners.stream().filter(user -> user.getId().equals(getUser().getId())).findAny();
    return planner.isPresent();
  }

  @Deprecated(since = "1.12.0", forRemoval = true)
  public boolean isExerciseObserver(String exerciseId) {
    return isSimulationObserver(exerciseId);
  }

  /**
   * Check that a user is a observer for a given simulation
   *
   * @param simulationId the simulation to check
   * @return true if the user is an observer for given simulation
   */
  public boolean isSimulationObserver(String simulationId) {
    if (isUserHasBypass()) {
      return true;
    }
    Exercise exercise = exerciseRepository.findById(simulationId).orElseThrow();
    List<User> observers = exercise.getObservers();
    Optional<User> observer =
        observers.stream().filter(user -> user.getId().equals(getUser().getId())).findAny();
    return observer.isPresent();
  }

  public boolean isExercisePlayer(String exerciseId) {
    if (isUserHasBypass()) {
      return true;
    }
    Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
    List<User> players = exercise.getUsers();
    Optional<User> player =
        players.stream().filter(user -> user.getId().equals(getUser().getId())).findAny();
    return player.isPresent();
  }

  // endregion

  // region user annotations
  public boolean isPlanner() {
    if (isUserHasBypass()) {
      return true;
    }
    User user = this.userRepository.findById(getUser().getId()).orElseThrow();
    return user.isPlanner();
  }

  public boolean isObserver() {
    if (isUserHasBypass()) {
      return true;
    }
    User user = userRepository.findById(getUser().getId()).orElseThrow();
    return user.isObserver();
  }

  public boolean isPlayer() {
    User user = userRepository.findById(getUser().getId()).orElseThrow();
    return user.isPlayer();
  }

  // endregion

  // region setters
  @Override
  public Object getFilterObject() {
    return this.filterObject;
  }

  @Override
  public void setFilterObject(Object obj) {
    this.filterObject = obj;
  }

  @Override
  public Object getReturnObject() {
    return this.returnObject;
  }

  @Override
  public void setReturnObject(Object obj) {
    this.returnObject = obj;
  }

  @Override
  public Object getThis() {
    return this;
  }
  // endregion
}

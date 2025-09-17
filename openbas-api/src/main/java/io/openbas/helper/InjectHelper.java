package io.openbas.helper;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Stream.concat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.database.model.*;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.specification.InjectSpecification;
import io.openbas.execution.ExecutableInject;
import io.openbas.execution.ExecutionContext;
import io.openbas.execution.ExecutionContextService;
import jakarta.annotation.Resource;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Component
@RequiredArgsConstructor
public class InjectHelper {

  @Resource protected ObjectMapper mapper;

  private final InjectRepository injectRepository;
  private final ExecutionContextService executionContextService;

  private List<Team> getInjectTeams(@NotNull final Inject inject) {
    Exercise exercise = inject.getExercise();
    if (inject
        .isAllTeams()) { // In order to process expectations from players, we also need to load
      // players into teams
      exercise.getTeams().forEach(team -> Hibernate.initialize(team.getUsers()));
      return exercise.getTeams();
    } else {
      inject.getTeams().forEach(team -> Hibernate.initialize(team.getUsers()));
      return inject.getTeams();
    }
  }

  // -- INJECTION --

  private Stream<Tuple2<User, String>> getUsersFromInjection(Injection injection) {
    if (injection instanceof Inject inject) {
      List<Team> teams = getInjectTeams(inject);
      // We get all the teams for this inject
      // But those team can be used in other exercises with different players enabled
      // So we need to focus on team players only enabled in the context of the current exercise
      if (inject.getInject().isAtomicTesting()) {
        return teams.stream()
            .flatMap(team -> team.getUsers().stream().map(user -> Tuples.of(user, team.getName())));
      }
      return teams.stream()
          .flatMap(
              team ->
                  team.getExerciseTeamUsers().stream()
                      .filter(
                          exerciseTeamUser ->
                              exerciseTeamUser
                                  .getExercise()
                                  .getId()
                                  .equals(injection.getExercise().getId()))
                      .map(
                          exerciseTeamUser ->
                              Tuples.of(exerciseTeamUser.getUser(), team.getName())));
    }
    throw new UnsupportedOperationException("Unsupported type of Injection");
  }

  private List<ExecutionContext> usersFromInjection(Injection injection) {
    return getUsersFromInjection(injection).collect(groupingBy(Tuple2::getT1)).entrySet().stream()
        .map(
            entry ->
                this.executionContextService.executionContext(
                    entry.getKey(),
                    injection,
                    entry.getValue().stream().flatMap(ua -> Stream.of(ua.getT2())).toList()))
        .toList();
  }

  private boolean isBeforeOrEqualsNow(Injection injection) {
    Instant now = Instant.now();
    Instant injectWhen = injection.getDate().orElseThrow();
    return injectWhen.equals(now) || injectWhen.isBefore(now);
  }

  public List<Inject> getAllPendingInjectsWithThresholdMinutes(int thresholdMinutes) {
    return this.injectRepository.findAll(
        InjectSpecification.pendingInjectWithThresholdMinutes(thresholdMinutes));
  }

  // -- EXECUTABLE INJECT --

  @Transactional
  public List<ExecutableInject> getInjectsToRun() {
    // Get injects
    List<Inject> injects = this.injectRepository.findAll(InjectSpecification.executable());
    Stream<ExecutableInject> executableInjects =
        injects.stream()
            .filter(this::isBeforeOrEqualsNow)
            .sorted(Inject.executionComparator)
            .map(
                inject -> {
                  // TODO This is inefficient, we need to refactor this loop with our own query
                  Hibernate.initialize(inject.getTags());
                  Hibernate.initialize(inject.getUser());
                  return new ExecutableInject(
                      true,
                      false,
                      inject,
                      getInjectTeams(inject),
                      inject.getAssets(), // TODO There is also inefficient lazy loading inside this
                      // get function
                      inject.getAssetGroups(),
                      usersFromInjection(inject));
                });
    // Get atomic testing injects
    List<Inject> atomicTests =
        this.injectRepository.findAll(InjectSpecification.forAtomicTesting());
    Stream<ExecutableInject> executableAtomicTests =
        atomicTests.stream()
            .filter(this::isBeforeOrEqualsNow)
            .sorted(Inject.executionComparator)
            .map(
                inject -> {
                  Hibernate.initialize(inject.getTags());
                  Hibernate.initialize(inject.getUser());
                  return new ExecutableInject(
                      true,
                      false,
                      inject,
                      getInjectTeams(inject),
                      inject.getAssets(),
                      inject.getAssetGroups(),
                      usersFromInjection(inject));
                });
    // Combine injects
    return concat(executableInjects, executableAtomicTests).collect(Collectors.toList());
  }
}

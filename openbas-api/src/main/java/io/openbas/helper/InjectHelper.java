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
import io.openbas.rest.injector_contract.InjectorContractContentUtils;
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

  // -- INJECT --
  /**
   * Builds an Inject object based on the provided InjectorContract, title, description and enabled
   *
   * @param injectorContract the InjectorContract associated with the Inject
   * @param title the title of the Inject
   * @param description the description of the Inject
   * @param enabled indicates whether the Inject is enabled or not
   * @return the inject object built
   */
  public static Inject buildInject(
      InjectorContract injectorContract, String title, String description, Boolean enabled) {
    Inject inject = new Inject();
    inject.setTitle(title);
    inject.setDescription(description);
    inject.setInjectorContract(injectorContract);
    inject.setDependsDuration(0L);
    inject.setEnabled(enabled);
    inject.setContent(
        InjectorContractContentUtils.getDynamicInjectorContractFieldsForInject(injectorContract));
    return inject;
  }

  /**
   * Builds a technical Inject object from the provided InjectorContract and AttackPattern.
   *
   * @param injectorContract the InjectorContract to build the Inject from
   * @param identifier the AttackPattern or Vulnerability associated with the Inject
   * @param name the AttackPattern or Vulnerability associated with the Inject
   * @return the built Inject object
   */
  public static Inject buildTechnicalInject(
      InjectorContract injectorContract, String identifier, String name) {
    return buildInject(
        injectorContract,
        String.format("[%s] %s - %s", identifier, name, injectorContract.getLabels().get("en")),
        null,
        true);
  }

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

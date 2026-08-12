package io.openaev.helper;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Stream.concat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.specification.InjectSpecification;
import io.openaev.execution.ExecutableInject;
import io.openaev.execution.ExecutionContext;
import io.openaev.execution.ExecutionContextService;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

/**
 * Helper component for inject operations and execution context building.
 *
 * <p>Provides methods for retrieving pending injects, converting injects to executable form, and
 * building execution contexts with team and user information. This component is central to the
 * inject execution pipeline.
 *
 * @see io.openaev.execution.ExecutableInject
 * @see io.openaev.execution.ExecutionContext
 */
@Component
@RequiredArgsConstructor
public class InjectHelper {

  @Resource protected ObjectMapper mapper;

  @Value("${inject.execution.batch-size:500}")
  private int batchSize;

  private final InjectRepository injectRepository;
  private final ExecutionContextService executionContextService;
  private final EntityManager entityManager;
  private final TenantScopedTransaction tenantTx;

  /**
   * Retrieves the teams targeted by an inject.
   *
   * <p>If the inject targets all teams, returns all teams from the exercise. Otherwise, returns
   * only the specifically targeted teams. Also initializes users within teams for player
   * expectation processing.
   *
   * @param inject the inject to get teams for
   * @return list of targeted teams with initialized users
   */
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
      if (inject.isAtomicTesting()) {
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

  /**
   * Retrieves all pending injects within a time threshold.
   *
   * <p>Finds injects that are pending execution and scheduled within the specified number of
   * minutes from now. Used for pre-loading upcoming injects.
   *
   * @param thresholdMinutes the time window in minutes to look ahead
   * @return list of pending injects scheduled within the threshold
   */
  public List<Inject> getAllStalledInjectsWithThresholdMinutes(int thresholdMinutes) {
    // Disable tenant filter — called from InjectsExecutionJob which runs cross-tenant
    entityManager.unwrap(Session.class).disableFilter("tenantFilter");
    return this.injectRepository.findAll(
        InjectSpecification.stalledInjectWithThresholdMinutes(thresholdMinutes));
  }

  // -- EXECUTABLE INJECT --

  private ExecutableInject toExecutableInject(Inject inject) {
    // TODO This is inefficient, we need to refactor this with our own query
    Hibernate.initialize(inject.getTags());
    Hibernate.initialize(inject.getUser());
    // The contract's attack patterns are fetched eagerly, but their kill chain phases are lazy.
    // External injectors serialize the whole inject (detached, outside any session) in
    // Executor.executeExternal, so initialize them here while the session is still open to
    // avoid a LazyInitializationException on attack_pattern_kill_chain_phases.
    inject
        .getInjectorContract()
        .ifPresent(
            injectorContract ->
                injectorContract
                    .getAttackPatterns()
                    .forEach(ap -> Hibernate.initialize(ap.getKillChainPhases())));
    return new ExecutableInject(
        true,
        false,
        inject,
        getInjectTeams(inject),
        inject.getAssets(), // TODO There is also inefficient lazy loading inside this get function
        inject.getAssetGroups(),
        usersFromInjection(inject));
  }

  /**
   * Retrieves all injects that are ready for execution.
   *
   * <p>Combines regular exercise injects and atomic testing injects that are scheduled for now or
   * earlier, converting them to executable form with all necessary context (teams, assets, users).
   *
   * <p>Genuinely cross-tenant: opens through {@link TenantScopedTransaction#execute} with {@link
   * TxCtx#allTenants()} instead of a plain {@code @Transactional}. A plain {@code @Transactional}
   * here leaves {@code app.current_tenants} unset for the whole read, which {@code
   * can_access_tenant} treats as fail-closed (no rows visible for ANY tenant, not just the wrong
   * one). The eager {@code Agent.executor} join hits the already-activated (v2) {@code executors}
   * table, so with no scope set it silently resolves to {@code null} for every agent, regardless of
   * tenant or executor type — this is what caused "No asset executed" on every launch.
   *
   * @return list of executable injects ready to run, sorted by execution order
   */
  public List<ExecutableInject> getInjectsToRun() {
    return tenantTx.execute(
        TxCtx.allTenants(),
        () -> {
          // Get injects whose planned date can already be reached (coarse SQL filter); the exact
          // pause-aware date check is still applied in memory below
          List<Inject> injects =
              this.injectRepository
                  .findAll(
                      InjectSpecification.executable()
                          .and(InjectSpecification.plannedDateReachable(Instant.now())),
                      PageRequest.ofSize(batchSize))
                  .getContent();
          Stream<ExecutableInject> executableInjects =
              injects.stream()
                  .filter(this::isBeforeOrEqualsNow)
                  .sorted(Inject.executionComparator)
                  .map(this::toExecutableInject);
          // Get atomic testing injects
          List<Inject> atomicTests =
              this.injectRepository
                  .findAll(InjectSpecification.forAtomicTesting(), PageRequest.ofSize(batchSize))
                  .getContent();
          Stream<ExecutableInject> executableAtomicTests =
              atomicTests.stream()
                  .filter(this::isBeforeOrEqualsNow)
                  .sorted(Inject.executionComparator)
                  .map(this::toExecutableInject);
          // Combine injects
          return concat(executableInjects, executableAtomicTests).collect(Collectors.toList());
        });
  }
}

package io.openaev.healthcheck.utils;

import static java.time.Instant.now;

import io.openaev.database.model.*;
import io.openaev.executors.utils.ExecutorUtils;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.helper.InjectModelHelper;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HealthCheckUtils {

  private final ExecutorUtils executorUtils;

  /**
   * Run all mail service checks for one inject
   *
   * @param inject to test
   * @param service to verify
   * @param isServiceAvailable status
   * @param type of healthcheck
   * @param status of healthcheck
   * @return found healthchecks
   */
  public List<HealthCheck> runMailServiceChecks(
      Inject inject,
      ExternalServiceDependency service,
      boolean isServiceAvailable,
      HealthCheck.Type type,
      HealthCheck.Status status) {
    List<HealthCheck> result = new ArrayList<>();
    InjectorContract injectorContract = inject.getInjectorContract().orElse(null);
    Injector injector = injectorContract != null ? injectorContract.getInjector() : null;

    if (injector != null
        && ArrayUtils.contains(injector.getDependencies(), service)
        && !isServiceAvailable) {
      result.add(new HealthCheck(type, HealthCheck.Detail.SERVICE_UNAVAILABLE, status, now()));
    }

    return result;
  }

  /**
   * Run all Executors checks for one inject
   *
   * @param inject to test
   * @param agentsAndAssetsAgentless data to verify if there is at least one agent up
   * @return all found executors healthchecks issues
   */
  public List<HealthCheck> runExecutorChecks(
      Inject inject, AgentsAndAssetsAgentless agentsAndAssetsAgentless) {
    List<HealthCheck> result = new ArrayList<>();
    InjectorContract injectorContract = inject.getInjectorContract().orElse(null);
    Set<Agent> agents = agentsAndAssetsAgentless.agents();
    agents = executorUtils.removeInactiveAgentsFromAgents(agents);
    agents = executorUtils.removeAgentsWithoutExecutorFromAgents(agents);

    if (injectorContract != null && injectorContract.getNeedsExecutor() && agents.isEmpty()) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.AGENT_OR_EXECUTOR,
              HealthCheck.Detail.EMPTY,
              HealthCheck.Status.ERROR,
              now()));
    }

    return result;
  }

  /**
   * Run all Collectors checks for one inject
   *
   * @param inject to test
   * @param collectors all available collectors
   * @return all found collectors healthchecks issues
   */
  public List<HealthCheck> runCollectorChecks(Inject inject, List<Collector> collectors) {
    List<HealthCheck> result = new ArrayList<>();
    boolean isDetectionOrPrenvention =
        InjectModelHelper.isDetectionOrPrevention(inject.getContent());

    if (isDetectionOrPrenvention && collectors.isEmpty()) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR,
              HealthCheck.Detail.EMPTY,
              HealthCheck.Status.ERROR,
              now()));
    }

    return result;
  }

  /**
   * Launch all the injector checks on one inject
   *
   * @param inject to test
   * @param injectors all available injectors
   * @return list of the healthcheck result
   */
  public List<HealthCheck> runAllInjectorChecks(
      @NotNull final Inject inject, @NotNull final List<Injector> injectors) {

    List<HealthCheck> results = new ArrayList<>();
    results.addAll(
        runInjectorCheck(inject, injectors, ExternalServiceDependency.NMAP, HealthCheck.Type.NMAP));
    results.addAll(
        runInjectorCheck(
            inject, injectors, ExternalServiceDependency.NUCLEI, HealthCheck.Type.NUCLEI));
    return results;
  }

  /**
   * Verify whether an injector contract depends on an injector and whether that injector is
   * registered; if not, add an error to the health check.
   *
   * @param inject
   * @param injectors
   * @param externalServiceDependency
   * @param type
   * @return
   */
  public List<HealthCheck> runInjectorCheck(
      @NotNull final Inject inject,
      @NotNull final List<Injector> injectors,
      @NotNull final ExternalServiceDependency externalServiceDependency,
      @NotNull final HealthCheck.Type type) {
    List<HealthCheck> result = new ArrayList<>();
    InjectorContract contract = inject.getInjectorContract().orElse(null);
    if (contract != null
        && contract.getInjector() != null
        && contract.getInjector().getDependencies() != null
        && Arrays.asList(contract.getInjector().getDependencies())
            .contains(externalServiceDependency)) {
      boolean isInjectorRegistered =
          injectors.stream()
              .anyMatch(
                  injector ->
                      Objects.equals(injector.getType(), externalServiceDependency.getValue()));

      // if the injector is not registered we add an error in the health check
      if (!isInjectorRegistered) {
        result.add(
            new HealthCheck(
                type, HealthCheck.Detail.SERVICE_UNAVAILABLE, HealthCheck.Status.ERROR, now()));
      }
    }
    return result;
  }

  /**
   * Run all missing content checks for one scenario
   *
   * @param scenario to test
   * @return all found missing content issues
   */
  public List<HealthCheck> runMissingContentChecks(@NotNull final Scenario scenario) {
    List<HealthCheck> result = new ArrayList<>();
    boolean atLeastOneInjectIsNotReady =
        scenario.getInjects().stream().anyMatch(inject -> !inject.isReady());

    if (atLeastOneInjectIsNotReady) {
      result.add(
          new HealthCheck(
              HealthCheck.Type.INJECT,
              HealthCheck.Detail.NOT_READY,
              HealthCheck.Status.WARNING,
              now()));
    }

    return result;
  }

  /**
   * Run all teams checks for one scenario
   *
   * @param scenario to test
   * @return all found teams issues
   */
  public List<HealthCheck> runTeamsChecks(@NotNull final Scenario scenario) {
    List<HealthCheck> result = new ArrayList<>();
    boolean isMailSender =
        scenario.getInjects().stream()
            .filter(
                inject ->
                    inject.getInjectorContract() != null
                        && inject.getInjectorContract().isPresent()
                        && inject.getInjectorContract().get().getInjector() != null
                        && inject.getInjectorContract().get().getInjector().getDependencies()
                            != null)
            .flatMap(
                inject ->
                    Arrays.stream(
                        inject.getInjectorContract().get().getInjector().getDependencies()))
            .anyMatch(
                dependency ->
                    ExternalServiceDependency.SMTP.equals(dependency)
                        || ExternalServiceDependency.IMAP.equals(dependency));

    if (isMailSender) {
      boolean isMissingTeamsOrEnabledPlayers =
          scenario.getTeams().isEmpty()
              || scenario.getTeams().stream().allMatch(team -> team.getUsers().isEmpty())
              || scenario.getTeamUsers().isEmpty();

      if (isMissingTeamsOrEnabledPlayers) {
        result.add(
            new HealthCheck(
                HealthCheck.Type.TEAMS,
                HealthCheck.Detail.EMPTY,
                HealthCheck.Status.WARNING,
                now()));
      }
    }

    return result;
  }
}

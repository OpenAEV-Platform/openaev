package io.openaev.healthcheck.utils;

import static java.time.Instant.now;

import io.openaev.database.model.*;
import io.openaev.executors.utils.ExecutorUtils;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.helper.InjectModelHelper;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.output.InjectOutput;
import io.openaev.rest.scenario.response.ScenarioOutput;
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
   * Run all missing content checks for one scenario
   *
   * @param scenarioOutput to test
   * @return all found missing content issues
   */
  public List<HealthCheck> runMissingContentChecks(ScenarioOutput scenarioOutput) {
    List<HealthCheck> result = new ArrayList<>();
    boolean atLeastOneInjectIsNotReady =
        scenarioOutput.getInjects().stream().anyMatch(inject -> !inject.isReady());

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
   * @param scenarioOutput to test
   * @return all found teams issues
   */
  public List<HealthCheck> runTeamsChecks(ScenarioOutput scenarioOutput) {
    List<HealthCheck> result = new ArrayList<>();
    boolean isMailSender =
        scenarioOutput.getInjects().stream()
            .filter(
                inject ->
                    inject.getInjectorContract() != null
                        && inject.getInjectorContract().getInjector() != null)
            .flatMap(
                inject ->
                    Arrays.stream(inject.getInjectorContract().getInjector().getDependencies()))
            .anyMatch(
                dependency ->
                    ExternalServiceDependency.SMTP.equals(dependency)
                        || ExternalServiceDependency.IMAP.equals(dependency));

    if (isMailSender) {
      boolean isMissingTeamsOrEnabledPlayers =
          scenarioOutput.getTeams().isEmpty()
              || scenarioOutput.getTeams().stream().allMatch(team -> team.getUsers().isEmpty())
              || scenarioOutput.getTeamUsers().isEmpty();

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

  /**
   * Run checks to find if at least one inject have the search error type into scenario
   *
   * @param scenarioOutput to test
   * @return all found agent or executor issues
   */
  /**
   * Run checks to find if at least one inject have the search error type into scenario
   *
   * @param scenarioOutput to test
   * @param type to find into injects
   * @param detail to set in case of error detection
   * @param status to set in case of error detection
   * @return found healthchecks
   */
  public List<HealthCheck> runInjectsInErrorChecks(
      ScenarioOutput scenarioOutput,
      HealthCheck.Type type,
      HealthCheck.Detail detail,
      HealthCheck.Status status) {
    List<HealthCheck> allInjectsHealthChecks = getAllInjectHealthChecks(scenarioOutput);
    List<HealthCheck> result = new ArrayList<>();

    if (!allInjectsHealthChecks.isEmpty() && anyMatch(allInjectsHealthChecks, type)) {
      result.add(new HealthCheck(type, detail, status, now()));
    }

    return result;
  }

  /**
   * Verify if an healthcheck type is found in a list of healthchecks
   *
   * @param healthChecks to test
   * @param type to found
   * @return true if type is found, false if not
   */
  private boolean anyMatch(List<HealthCheck> healthChecks, HealthCheck.Type type) {
    return healthChecks.stream().anyMatch(healthCheck -> type.equals(healthCheck.getType()));
  }

  /**
   * Return all Healthchecks of all the inject on a scenario
   *
   * @param scenarioOutput to get all injects healthchecks
   * @return a list of all the founded healthchecks
   */
  private List<HealthCheck> getAllInjectHealthChecks(ScenarioOutput scenarioOutput) {
    return scenarioOutput.getInjects().stream()
        .map(InjectOutput::getHealthchecks)
        .flatMap(List::stream)
        .toList();
  }
}

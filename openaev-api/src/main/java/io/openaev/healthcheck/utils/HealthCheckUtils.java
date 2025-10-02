package io.openaev.healthcheck.utils;

import io.openaev.database.model.*;
import io.openaev.executors.utils.ExecutorUtils;
import io.openaev.healthcheck.dto.HealthCheck;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import io.openaev.helper.InjectModelHelper;
import io.openaev.rest.inject.output.AgentsAndAssetsAgentless;
import io.openaev.rest.inject.output.InjectOutput;
import io.openaev.rest.scenario.response.ScenarioOutput;
import java.util.*;
import org.apache.commons.lang3.ArrayUtils;

public class HealthCheckUtils {

  /**
   * Run all SMTP checks for one inject
   *
   * @param inject to test
   * @param isSmtpServiceAvailable represent the status of the service, true if available, false if
   *     not
   * @return all found smtp healthchecks issues
   */
  public static List<HealthCheck> runSmtpChecks(Inject inject, boolean isSmtpServiceAvailable) {
    List<HealthCheck> result = new ArrayList<>();
    InjectorContract injectorContract = inject.getInjectorContract().orElse(null);
    Injector injector = injectorContract != null ? injectorContract.getInjector() : null;

    if (injector != null
        && ArrayUtils.contains(injector.getDependencies(), ExternalServiceDependency.SMTP)
        && !isSmtpServiceAvailable) {
      result.add(
          HealthCheckUtils.createErrorHealthCheck(
              HealthCheck.Type.SMTP, HealthCheck.Detail.SERVICE_UNAVAILABLE));
    }

    return result;
  }

  /**
   * Run all IMAP checks for one inject
   *
   * @param inject to test
   * @param isImapServiceAvailable represent the status of the service, true if available, false if
   *     not
   * @return all found smtp healthchecks issues
   */
  public static List<HealthCheck> runImapChecks(Inject inject, boolean isImapServiceAvailable) {
    List<HealthCheck> result = new ArrayList<>();
    InjectorContract injectorContract = inject.getInjectorContract().orElse(null);
    Injector injector = injectorContract != null ? injectorContract.getInjector() : null;

    if (injector != null
        && ArrayUtils.contains(injector.getDependencies(), ExternalServiceDependency.IMAP)
        && !isImapServiceAvailable) {
      result.add(
          HealthCheckUtils.createWarningHealthCheck(
              HealthCheck.Type.IMAP, HealthCheck.Detail.SERVICE_UNAVAILABLE));
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
  public static List<HealthCheck> runExecutorChecks(
      Inject inject, AgentsAndAssetsAgentless agentsAndAssetsAgentless) {
    List<HealthCheck> result = new ArrayList<>();
    InjectorContract injectorContract = inject.getInjectorContract().orElse(null);
    Set<Agent> agents = agentsAndAssetsAgentless.agents();
    agents = ExecutorUtils.removeInactiveAgentsFromAgents(agents);
    agents = ExecutorUtils.removeAgentsWithoutExecutorFromAgents(agents);

    if (injectorContract != null && injectorContract.getNeedsExecutor() && agents.isEmpty()) {
      result.add(
          HealthCheckUtils.createErrorHealthCheck(
              HealthCheck.Type.AGENT_OR_EXECUTOR, HealthCheck.Detail.EMPTY));
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
  public static List<HealthCheck> runCollectorChecks(Inject inject, List<Collector> collectors) {
    List<HealthCheck> result = new ArrayList<>();
    boolean isDetectionOrPrenvention =
        InjectModelHelper.isDetectionOrPrevention(inject.getContent());

    if (isDetectionOrPrenvention && collectors.isEmpty()) {
      result.add(
          HealthCheckUtils.createErrorHealthCheck(
              HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR, HealthCheck.Detail.EMPTY));
    }

    return result;
  }

  /**
   * Run all Agents or Executors checks for one scenario
   *
   * @param scenarioOutput to test
   * @return all found agent or executor issues
   */
  public static List<HealthCheck> runExecutorChecks(ScenarioOutput scenarioOutput) {
    List<HealthCheck> allInjectsHealthChecks = getAllInjectHealthChecks(scenarioOutput);
    List<HealthCheck> result = new ArrayList<>();

    if (!allInjectsHealthChecks.isEmpty()
        && anyMatch(allInjectsHealthChecks, HealthCheck.Type.AGENT_OR_EXECUTOR)) {
      result.add(
          createErrorHealthCheck(HealthCheck.Type.AGENT_OR_EXECUTOR, HealthCheck.Detail.EMPTY));
    }

    return result;
  }

  /**
   * Run all Security System Collector checks for one scenario
   *
   * @param scenarioOutput to test
   * @return all found security system collector issues
   */
  public static List<HealthCheck> runCollectorChecks(ScenarioOutput scenarioOutput) {
    List<HealthCheck> allInjectsHealthChecks = getAllInjectHealthChecks(scenarioOutput);
    List<HealthCheck> result = new ArrayList<>();

    if (!allInjectsHealthChecks.isEmpty()
        && anyMatch(allInjectsHealthChecks, HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR)) {
      result.add(
          createErrorHealthCheck(
              HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR, HealthCheck.Detail.EMPTY));
    }

    return result;
  }

  /**
   * Run all missing content checks for one scenario
   *
   * @param scenarioOutput to test
   * @return all found missing content issues
   */
  public static List<HealthCheck> runMissingContentChecks(ScenarioOutput scenarioOutput) {
    List<HealthCheck> result = new ArrayList<>();
    boolean atLeastOneInjectIsNotReady =
        scenarioOutput.getInjects().stream().anyMatch(inject -> !inject.isReady());

    if (atLeastOneInjectIsNotReady) {
      result.add(createWarningHealthCheck(HealthCheck.Type.INJECT, HealthCheck.Detail.NOT_READY));
    }

    return result;
  }

  /**
   * Run all teams checks for one scenario
   *
   * @param scenarioOutput to test
   * @return all found teams issues
   */
  public static List<HealthCheck> runTeamsChecks(ScenarioOutput scenarioOutput) {
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
        result.add(createWarningHealthCheck(HealthCheck.Type.TEAMS, HealthCheck.Detail.EMPTY));
      }
    }

    return result;
  }

  /**
   * Run all SMTP checks for one scenario
   *
   * @param scenarioOutput to test
   * @return all found smtp healthchecks issues
   */
  public static List<HealthCheck> runSmtpChecks(ScenarioOutput scenarioOutput) {
    List<HealthCheck> allInjectsHealthChecks = getAllInjectHealthChecks(scenarioOutput);
    List<HealthCheck> result = new ArrayList<>();

    if (!allInjectsHealthChecks.isEmpty()
        && anyMatch(allInjectsHealthChecks, HealthCheck.Type.SMTP)) {
      result.add(
          createErrorHealthCheck(HealthCheck.Type.SMTP, HealthCheck.Detail.SERVICE_UNAVAILABLE));
    }

    return result;
  }

  /**
   * Run all IMAP checks for one scenario
   *
   * @param scenarioOutput to test
   * @return all found smtp healthchecks issues
   */
  public static List<HealthCheck> runImapChecks(ScenarioOutput scenarioOutput) {
    List<HealthCheck> allInjectsHealthChecks = getAllInjectHealthChecks(scenarioOutput);
    List<HealthCheck> result = new ArrayList<>();

    if (!allInjectsHealthChecks.isEmpty()
        && anyMatch(allInjectsHealthChecks, HealthCheck.Type.IMAP)) {
      result.add(
          createWarningHealthCheck(HealthCheck.Type.IMAP, HealthCheck.Detail.SERVICE_UNAVAILABLE));
    }

    return result;
  }

  /**
   * Create an Healthcheck in error state
   *
   * @param type of the healthcheck
   * @param detail of the healthcheck
   * @return healthcheck in error
   */
  private static HealthCheck createErrorHealthCheck(
      HealthCheck.Type type, HealthCheck.Detail detail) {
    return new HealthCheck(type, detail, HealthCheck.Status.ERROR, new Date());
  }

  /**
   * Create an Healthcheck in warning state
   *
   * @param type of the healthcheck
   * @param detail of the healthcheck
   * @return healthcheck in warning
   */
  private static HealthCheck createWarningHealthCheck(
      HealthCheck.Type type, HealthCheck.Detail detail) {
    return new HealthCheck(type, detail, HealthCheck.Status.WARNING, new Date());
  }

  /**
   * Verify if an healthcheck type is found in a list of healthchecks
   *
   * @param healthChecks to test
   * @param type to found
   * @return true if type is found, false if not
   */
  private static boolean anyMatch(List<HealthCheck> healthChecks, HealthCheck.Type type) {
    return healthChecks.stream().anyMatch(healthCheck -> type.equals(healthCheck.getType()));
  }

  /**
   * Return all Healthchecks of all the inject on a scenario
   *
   * @param scenarioOutput to get all injects healthchecks
   * @return a list of all the founded healthchecks
   */
  private static List<HealthCheck> getAllInjectHealthChecks(ScenarioOutput scenarioOutput) {
    return scenarioOutput.getInjects().stream()
        .map(InjectOutput::getHealthchecks)
        .flatMap(List::stream)
        .toList();
  }
}

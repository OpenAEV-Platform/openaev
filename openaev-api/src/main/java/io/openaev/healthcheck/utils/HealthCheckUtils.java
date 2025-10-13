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
import io.openaev.service.InjectorService;
import java.util.*;

import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Qualifier;
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
   * Run all Injectors checks for one inject
   *
   * @param inject to test
   * @param injectors all available injectors
   * @return all found injectors healthchecks issues
   */
  public List<HealthCheck> runInjectorChecks(Inject inject, List<Injector> injectors) {
    List<HealthCheck> result = new ArrayList<>();
    InjectorContract contract = inject.getInjectorContract().orElse(null);
    if (contract != null
        && contract.getInjector() != null
        && contract.getInjector().getDependencies() != null
        && Arrays.asList(contract.getInjector().getDependencies())
            .contains(ExternalServiceDependency.NMAP)) {
        boolean isNmapInjectorRegistered = injectors.stream()
                .anyMatch(injector -> Objects.equals(injector.getType(), ExternalServiceDependency.NMAP.getValue()));

        // if the injector is dependent on NMAP and NMAP is not registered
      if (!isNmapInjectorRegistered) {
        result.add(
            new HealthCheck(
                HealthCheck.Type.NMAP,
                HealthCheck.Detail.EMPTY,
                HealthCheck.Status.ERROR,
                now()));
      }
    }

    if (contract != null
        && contract.getInjector() != null
        && contract.getInjector().getDependencies() != null
        && Arrays.asList(contract.getInjector().getDependencies())
            .contains(ExternalServiceDependency.NUCLEI)) {

        // if the injector is dependent on NUCLEI and NUCLEI is not registered
        boolean isNucleiInjectorRegistered = injectors.stream()
                .anyMatch(injector -> Objects.equals(injector.getType(), ExternalServiceDependency.NUCLEI.getValue()));

        if (!isNucleiInjectorRegistered) {
            result.add(
                    new HealthCheck(
                            HealthCheck.Type.NUCLEI,
                            HealthCheck.Detail.EMPTY,
                            HealthCheck.Status.ERROR,
                            now()));
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
                        && inject.getInjectorContract().get().getInjector().getDependencies() != null)
            .flatMap(
                inject ->
                    Arrays.stream(inject.getInjectorContract().get().getInjector().getDependencies()))
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

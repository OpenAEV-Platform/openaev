package io.openbas.healthcheck.utils;

import io.openbas.database.model.*;
import io.openbas.executors.utils.ExecutorUtils;
import io.openbas.healthcheck.dto.HealthCheck;
import io.openbas.healthcheck.enums.ExternalServiceDependency;
import io.openbas.helper.InjectModelHelper;
import io.openbas.rest.inject.output.AgentsAndAssetsAgentless;
import io.openbas.rest.inject.output.InjectOutput;
import io.openbas.rest.scenario.response.ScenarioOutput;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class HealthCheckUtils {

    /**
     * Run all SMTP checks for one inject
     * @param inject to test
     * @param isSmtpServiceAvailable represent the status of the service, true if available, false if not
     * @return all found smtp healthchecks issues
     */
    public static List<HealthCheck> runSmtpChecks(Inject inject, boolean isSmtpServiceAvailable) {
        List<HealthCheck> result = new ArrayList<>();
        InjectorContract injectorContract = inject.getInjectorContract().orElse(null);
        Injector injector = injectorContract != null ? injectorContract.getInjector() : null;

        if (injector != null && ArrayUtils.contains(injector.getDependencies(), ExternalServiceDependency.SMTP) && !isSmtpServiceAvailable) {
            result.add(HealthCheckUtils.createErrorHealthCheck(HealthCheck.Type.SMTP, HealthCheck.Detail.SERVICE_UNAVAILABLE));
        }

        return result;
    }

    /**
     * Run all IMAP checks for one inject
     * @param inject to test
     * @param isImapServiceAvailable represent the status of the service, true if available, false if not
     * @return all found smtp healthchecks issues
     */
    public static List<HealthCheck> runImapChecks(Inject inject, boolean isImapServiceAvailable) {
        List<HealthCheck> result = new ArrayList<>();
        InjectorContract injectorContract = inject.getInjectorContract().orElse(null);
        Injector injector = injectorContract != null ? injectorContract.getInjector() : null;

        if (injector != null && ArrayUtils.contains(injector.getDependencies(), ExternalServiceDependency.IMAP) && !isImapServiceAvailable) {
            result.add(HealthCheckUtils.createErrorHealthCheck(HealthCheck.Type.IMAP, HealthCheck.Detail.SERVICE_UNAVAILABLE));
        }

        return result;
    }

    /**
     * Run all Executors checks for one inject
     * @param inject to test
     * @param agentsAndAssetsAgentless data to verify if there is at least one agent up
     * @return all found executors healthchecks issues
     */
    public static List<HealthCheck> runExecutorChecks(Inject inject, AgentsAndAssetsAgentless agentsAndAssetsAgentless) {
        List<HealthCheck> result = new ArrayList<>();
        InjectorContract injectorContract = inject.getInjectorContract().orElse(null);
        Set<Agent> agents = agentsAndAssetsAgentless.agents();
        agents = ExecutorUtils.removeInactiveAgentsFromAgents(agents);
        agents = ExecutorUtils.removeAgentsWithoutExecutorFromagents(agents);
        agents = ExecutorUtils.removeCrowdstrikeAgentsFromagents(agents);

        if (injectorContract != null && injectorContract.getNeedsExecutor() && agents.isEmpty()) {
            result.add(HealthCheckUtils.createErrorHealthCheck(HealthCheck.Type.AGENT_OR_EXECUTOR, HealthCheck.Detail.EMPTY));
        }

        return result;
    }

    /**
     * Run all Collectors checks for one inject
     * @param inject to test
     * @param collectors all available collectors
     * @return all found collectors healthchecks issues
     */
    public static List<HealthCheck> runCollectorChecks(Inject inject, List<Collector> collectors) {
        List<HealthCheck> result = new ArrayList<>();
        boolean isDetectionOrPrenvention = InjectModelHelper.isDetectionOrPrevention(inject.getContent());

        if (isDetectionOrPrenvention && collectors.isEmpty()) {
            result.add(HealthCheckUtils.createErrorHealthCheck(HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR, HealthCheck.Detail.EMPTY));
        }

        return result;
    }

    /**
     * Run all Agents or Executors checks for one scenario
     * @param scenarioOutput to test
     * @return all found agent or executor issues
     */
    public static List<HealthCheck> runExecutorChecks(ScenarioOutput scenarioOutput) {
        List<HealthCheck> allInjectsHealthChecks = getAllInjectHealthChecks(scenarioOutput);
        List<HealthCheck> result = new ArrayList<>();

        if (!allInjectsHealthChecks.isEmpty() && anyMatch(allInjectsHealthChecks, HealthCheck.Type.AGENT_OR_EXECUTOR)) {
            result.add(createErrorHealthCheck(HealthCheck.Type.AGENT_OR_EXECUTOR, HealthCheck.Detail.EMPTY));
        }

        return result;
    }

    /**
     * Run all Security System Collector checks for one scenario
     * @param scenarioOutput to test
     * @return all found security system collector issues
     */
    public static List<HealthCheck> runCollectorChecks(ScenarioOutput scenarioOutput) {
        List<HealthCheck> allInjectsHealthChecks = getAllInjectHealthChecks(scenarioOutput);
        List<HealthCheck> result = new ArrayList<>();

        if (!allInjectsHealthChecks.isEmpty() && anyMatch(allInjectsHealthChecks, HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR)) {
            result.add(createErrorHealthCheck(HealthCheck.Type.SECURITY_SYSTEM_COLLECTOR, HealthCheck.Detail.EMPTY));
        }

        return result;
    }

    /**
     * Run all SMTP checks for one scenario
     * @param scenarioOutput to test
     * @return all found smtp healthchecks issues
     */
    public static List<HealthCheck> runSmtpChecks(ScenarioOutput scenarioOutput) {
        List<HealthCheck> allInjectsHealthChecks = getAllInjectHealthChecks(scenarioOutput);
        List<HealthCheck> result = new ArrayList<>();

        if (!allInjectsHealthChecks.isEmpty() && anyMatch(allInjectsHealthChecks, HealthCheck.Type.SMTP)) {
            result.add(createErrorHealthCheck(HealthCheck.Type.SMTP, HealthCheck.Detail.SERVICE_UNAVAILABLE));
        }

        return result;
    }

    /**
     * Run all IMAP checks for one scenario
     * @param scenarioOutput to test
     * @return all found smtp healthchecks issues
     */
    public static List<HealthCheck> runImapChecks(ScenarioOutput scenarioOutput) {
        List<HealthCheck> allInjectsHealthChecks = getAllInjectHealthChecks(scenarioOutput);
        List<HealthCheck> result = new ArrayList<>();

        if (!allInjectsHealthChecks.isEmpty() && anyMatch(allInjectsHealthChecks, HealthCheck.Type.IMAP)) {
            result.add(createErrorHealthCheck(HealthCheck.Type.IMAP, HealthCheck.Detail.SERVICE_UNAVAILABLE));
        }

        return result;
    }

    /**
     * Create an Healthcheck in error state
     * @param type of the healthcheck
     * @param detail of the healthcheck
     * @return healthcheck in error
     */
    public static HealthCheck createErrorHealthCheck(HealthCheck.Type type, HealthCheck.Detail detail) {
        return new HealthCheck(
                type,
                detail,
                HealthCheck.Status.ERROR,
                new Date()
        );
    }

    /**
     * Verify if an healthcheck type is found in a list of healthchecks
     * @param healthChecks to test
     * @param type to found
     * @return true if type is found, false if not
     */
    public static boolean anyMatch(List<HealthCheck> healthChecks, HealthCheck.Type type) {
        return healthChecks.stream().anyMatch(healthCheck -> type.equals(healthCheck.getType()));
    }

    /**
     * Return all Healthchecks of all the inject on a scenario
     * @param scenarioOutput to get all injects healthchecks
     * @return a list of all the founded healthchecks
     */
    public static List<HealthCheck> getAllInjectHealthChecks(ScenarioOutput scenarioOutput) {
        return scenarioOutput.getInjects().stream()
                .map(InjectOutput::getHealthchecks)
                .flatMap(List::stream)
                .toList();
    }
}

package io.openaev.executors.sentinelone.service;

import static io.openaev.executors.sentinelone.service.SentinelOneExecutorService.SENTINELONE_EXECUTOR_NAME;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.crowdstrike.model.CrowdStrikeAction;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Slf4j
@Service(SentinelOneExecutorContextService.SERVICE_NAME)
public class SentinelOneExecutorContextService extends ExecutorContextService {
  public static final String SERVICE_NAME = SENTINELONE_EXECUTOR_NAME;

    private static final int SLEEP_INTERVAL_BATCH_EXECUTIONS = 1000;

    private static final String AGENT_ID_VARIABLE = "$agentID";

    // TODO
    private static final String WINDOWS_EXTERNAL_REFERENCE =
            "$agentID=[System.BitConverter]::ToString(((Get-ItemProperty 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\CSAgent\\Sim').AG)).ToLower() -replace '-','';";
    private static final String LINUX_EXTERNAL_REFERENCE =
            "agentID=$(sudo /opt/CrowdStrike/falconctl -g --aid | sed 's/aid=\"//g' | sed 's/\".//g');";
    private static final String MAC_EXTERNAL_REFERENCE =
            "agentID=$(sudo /Applications/Falcon.app/Contents/Resources/falconctl stats | grep agentID | sed 's/agentID: //g' | tr '[:upper:]' '[:lower:]' | sed 's/-//g');";

    private final SentinelOneExecutorConfig config;
    private final SentinelOneExecutorClient client;
    private final Ee eeService;
    private final LicenseCacheManager licenseCacheManager;
    private final ExecutionTraceRepository executionTraceRepository;

  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent) {
      // launchBatchExecutorSubprocess is used here for better performances
  }

  @Override
  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus) throws InterruptedException {

      eeService.throwEEExecutorService(
              licenseCacheManager.getEnterpriseEditionInfo(), SERVICE_NAME, injectStatus);

      if (!this.config.isEnable()) {
          throw new RuntimeException("Fatal error: SentinelOne executor is not enabled");
      }
      List<Agent> sentinelOneAgents = new ArrayList<>(agents);

      // Sometimes, assets from agents aren't fetched even with the EAGER property from Hibernate
      sentinelOneAgents.forEach(agent -> agent.setAsset((Asset) Hibernate.unproxy(agent.getAsset())));

      Injector injector =
              inject
                      .getInjectorContract()
                      .map(InjectorContract::getInjector)
                      .orElseThrow(
                              () -> new UnsupportedOperationException("Inject does not have a contract"));

      sentinelOneAgents = manageWithoutPlatformAgents(sentinelOneAgents, injectStatus);
      List<CrowdStrikeAction> actions = new ArrayList<>();
      // Set implant script for Windows SentinelOne agents
     /* actions.addAll(
              getWindowsActions(
                      getAgentsFromOS(sentinelOneAgents, Endpoint.PLATFORM_TYPE.Windows), injector, inject.getId()));
      // Set implant script for Linux SentinelOne agents
      actions.addAll(
              getLinuxActions(
                      getAgentsFromOS(sentinelOneAgents, Endpoint.PLATFORM_TYPE.Linux), injector, inject.getId()));
      // Set implant script for MacOS SentinelOne agents
      actions.addAll(
              getMacOSActions(
                      getAgentsFromOS(sentinelOneAgents, Endpoint.PLATFORM_TYPE.MacOS), injector, inject.getId()));
      // Launch payloads with SentinelOne API
      executeActions(actions);*/
      return sentinelOneAgents;
  }

  // TODO refactor with CS to ExecutorService
    private List<Agent> manageWithoutPlatformAgents(List<Agent> agents, InjectStatus injectStatus) {
        List<Agent> sentinelOneAgents = new ArrayList<>(agents);
        List<Agent> withoutPlatformAgents =
                sentinelOneAgents.stream()
                        .filter(
                                agent ->
                                        ((Endpoint) agent.getAsset()).getPlatform() == null
                                                || ((Endpoint) agent.getAsset()).getPlatform()
                                                == Endpoint.PLATFORM_TYPE.Unknown
                                                || ((Endpoint) agent.getAsset()).getArch() == null)
                        .toList();
        sentinelOneAgents.removeAll(withoutPlatformAgents);
        // Agents with no platform or unknown platform, traces to save
        if (!withoutPlatformAgents.isEmpty()) {
            executionTraceRepository.saveAll(
                    withoutPlatformAgents.stream()
                            .map(
                                    agent ->
                                            new ExecutionTrace(
                                                    injectStatus,
                                                    ExecutionTraceStatus.ERROR,
                                                    List.of(),
                                                    "Unsupported platform: "
                                                            + ((Endpoint) agent.getAsset()).getPlatform()
                                                            + " (arch:"
                                                            + ((Endpoint) agent.getAsset()).getArch()
                                                            + ")",
                                                    ExecutionTraceAction.COMPLETE,
                                                    agent,
                                                    null))
                            .toList());
        }
        return sentinelOneAgents;
    }

    /*private void executeActions(List<CrowdStrikeAction> actions) throws InterruptedException {
        for (CrowdStrikeAction action : actions) {
            int paginationLimit = this.crowdStrikeExecutorConfig.getApiBatchExecutionActionPagination();
            // Pagination with 1s wait if needed because each implant will call OpenAEV API to set traces
            if (action.getAgents().size() > paginationLimit) {
                int numberOfExecution = Math.ceilDiv(action.getAgents().size(), paginationLimit);
                int fromIndex = 0;
                int toIndex = paginationLimit;
                for (int callNumber = 0; callNumber < numberOfExecution; callNumber += 1) {
                    this.crowdStrikeExecutorClient.executeAction(
                            action.getAgents().subList(fromIndex, toIndex).stream().map(Agent::getId).toList(),
                            action.getScriptName(),
                            action.getCommandEncoded());
                    fromIndex = toIndex;
                    toIndex = Math.min(action.getAgents().size(), fromIndex + paginationLimit);
                    Thread.sleep(SLEEP_INTERVAL_BATCH_EXECUTIONS);
                }
            } else {
                this.crowdStrikeExecutorClient.executeAction(
                        action.getAgents().stream().map(Agent::getId).toList(),
                        action.getScriptName(),
                        action.getCommandEncoded());
                Thread.sleep(SLEEP_INTERVAL_BATCH_EXECUTIONS);
            }
        }
    }*/

}

package io.openaev.executors.crowdstrike.service;

import static io.openaev.executors.ExecutorHelper.replaceArgs;
import static io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService.CROWDSTRIKE_EXECUTOR_NAME;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.model.CrowdStrikeAction;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Slf4j
@Service(CrowdStrikeExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class CrowdStrikeExecutorContextService extends ExecutorContextService {
  public static final String SERVICE_NAME = CROWDSTRIKE_EXECUTOR_NAME;

  private static final int SLEEP_INTERVAL_BATCH_EXECUTIONS = 1000;

  private static final String AGENT_ID_VARIABLE = "$agentID";
  private static final String ARCH_VARIABLE = "$architecture";

  private static final String WINDOWS_EXTERNAL_REFERENCE =
      "$agentID=[System.BitConverter]::ToString(((Get-ItemProperty 'HKLM:\\SYSTEM\\CurrentControlSet\\Services\\CSAgent\\Sim').AG)).ToLower() -replace '-','';";
  private static final String LINUX_EXTERNAL_REFERENCE =
      "agentID=$(sudo /opt/CrowdStrike/falconctl -g --aid | sed 's/aid=\"//g' | sed 's/\".//g');";
  private static final String MAC_EXTERNAL_REFERENCE =
      "agentID=$(sudo /Applications/Falcon.app/Contents/Resources/falconctl stats | grep agentID | sed 's/agentID: //g' | tr '[:upper:]' '[:lower:]' | sed 's/-//g');";
  private static final String WINDOWS_ARCH =
      "switch ($env:PROCESSOR_ARCHITECTURE) { \"AMD64\" {$architecture = \"x86_64\"; Break} \"ARM64\" {$architecture = \"arm64\"; Break} \"x86\" { switch ($env:PROCESSOR_ARCHITEW6432) { \"AMD64\" {$architecture = \"x86_64\"; Break} \"ARM64\" {$architecture = \"arm64\"; Break} } } };";
  private static final String UNIX_ARCH = "architecture=$(uname -m);";

  private final CrowdStrikeExecutorConfig crowdStrikeExecutorConfig;
  private final CrowdStrikeExecutorClient crowdStrikeExecutorClient;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;
  private final ExecutionTraceRepository executionTraceRepository;

  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent) {}

  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus) throws InterruptedException {

    eeService.throwEEExecutorService(
        licenseCacheManager.getEnterpriseEditionInfo(), SERVICE_NAME, injectStatus);

    if (!this.crowdStrikeExecutorConfig.isEnable()) {
      throw new RuntimeException("Fatal error: CrowdStrike executor is not enabled");
    }
    List<Agent> csAgents = new ArrayList<>(agents);

    // Sometimes, assets from agents aren't fetched even with the EAGER property from Hibernate
    csAgents.forEach(agent -> agent.setAsset((Asset) Hibernate.unproxy(agent.getAsset())));

    Injector injector =
        inject
            .getInjectorContract()
            .map(InjectorContract::getInjector)
            .orElseThrow(
                () -> new UnsupportedOperationException("Inject does not have a contract"));

    csAgents = manageWithoutPlatformAgents(csAgents, injectStatus);
    List<CrowdStrikeAction> actions = new ArrayList<>();
    // Set implant script for Windows CS agents
    actions.addAll(
        getWindowsActions(
            getAgentsFromOS(csAgents, Endpoint.PLATFORM_TYPE.Windows), injector, inject.getId()));
    // Set implant script for Linux CS agents
    actions.addAll(
        getLinuxActions(
            getAgentsFromOS(csAgents, Endpoint.PLATFORM_TYPE.Linux), injector, inject.getId()));
    // Set implant script for MacOS CS agents
    actions.addAll(
        getMacOSActions(
            getAgentsFromOS(csAgents, Endpoint.PLATFORM_TYPE.MacOS), injector, inject.getId()));
    // Launch payloads with CS API
    executeActions(actions);
    return csAgents;
  }

  private List<Agent> getAgentsFromOS(List<Agent> agents, Endpoint.PLATFORM_TYPE platform) {
    return agents.stream()
        .filter(agent -> ((Endpoint) agent.getAsset()).getPlatform().equals(platform))
        .toList();
  }

  private List<Agent> manageWithoutPlatformAgents(List<Agent> agents, InjectStatus injectStatus) {
    List<Agent> csAgents = new ArrayList<>(agents);
    List<Agent> withoutPlatformAgents =
        csAgents.stream()
            .filter(
                agent ->
                    ((Endpoint) agent.getAsset()).getPlatform() == null
                        || ((Endpoint) agent.getAsset()).getPlatform()
                            == Endpoint.PLATFORM_TYPE.Unknown
                        || ((Endpoint) agent.getAsset()).getArch() == null)
            .toList();
    csAgents.removeAll(withoutPlatformAgents);
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
    return csAgents;
  }

  private void executeActions(List<CrowdStrikeAction> actions) throws InterruptedException {
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
  }

  private List<CrowdStrikeAction> getWindowsActions(
      List<Agent> agents, Injector injector, String injectId) {
    List<CrowdStrikeAction> actions = new ArrayList<>();
    if (!agents.isEmpty()) {
      CrowdStrikeAction actionWindows = new CrowdStrikeAction();
      actionWindows.setScriptName(this.crowdStrikeExecutorConfig.getWindowsScriptName());
      String implantLocation =
          "$location="
              + ExecutorHelper.IMPLANT_LOCATION_WINDOWS
              + ExecutorHelper.IMPLANT_BASE_NAME
              + UUID.randomUUID()
              + "\";md $location -ea 0;[Environment]::CurrentDirectory";
      Endpoint.PLATFORM_TYPE platform = Endpoint.PLATFORM_TYPE.Windows;
      // x86_64 by default in the register because CS API doesn't provide the platform architecture
      // (we update this when the download implant script is launched on the endpoint)
      String executorCommandKey = platform.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name();
      String command = injector.getExecutorCommands().get(executorCommandKey);
      // The default command to download the openaev implant and execute the attack is modified for
      // CS
      // - WINDOWS_ARCH: CS doesn't know the endpoint architecture so we include it to get the
      // architecture before downloading the implant and we replace the default x86_64 put before
      // - WINDOWS_EXTERNAL_REFERENCE: the agent id in the openAEV DB for CS is the CS agent id to
      // make the batch attack works so we get it with a command line from the endpoint and give it
      // to the implant
      command =
          WINDOWS_ARCH
              + WINDOWS_EXTERNAL_REFERENCE
              + command.replace(
                  Endpoint.PLATFORM_ARCH.x86_64.name(),
                  ARCH_VARIABLE
                      + "`"); // Specific for Windows to escape the ? right after in the URL
      command = replaceArgs(platform, command, injectId, AGENT_ID_VARIABLE);
      command =
          command.replaceFirst(
              "\\$?x=.+location=.+;\\[Environment]::CurrentDirectory",
              Matcher.quoteReplacement(implantLocation));
      actionWindows.setCommandEncoded(
          Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_16LE)));
      actionWindows.setAgents(agents);
      actions.add(actionWindows);
    }
    return actions;
  }

  private List<CrowdStrikeAction> getLinuxActions(
      List<Agent> agents, Injector injector, String injectId) {
    List<CrowdStrikeAction> actions = new ArrayList<>();
    if (!agents.isEmpty()) {
      CrowdStrikeAction actionLinux = new CrowdStrikeAction();
      actionLinux.setScriptName(this.crowdStrikeExecutorConfig.getUnixScriptName());
      actionLinux.setCommandEncoded(
          getUnixCommand(
              Endpoint.PLATFORM_TYPE.Linux, injector, injectId, LINUX_EXTERNAL_REFERENCE));
      actionLinux.setAgents(agents);
      actions.add(actionLinux);
    }
    return actions;
  }

  private List<CrowdStrikeAction> getMacOSActions(
      List<Agent> agents, Injector injector, String injectId) {
    List<CrowdStrikeAction> actions = new ArrayList<>();
    if (!agents.isEmpty()) {
      CrowdStrikeAction actionMac = new CrowdStrikeAction();
      actionMac.setScriptName(this.crowdStrikeExecutorConfig.getUnixScriptName());
      actionMac.setCommandEncoded(
          getUnixCommand(Endpoint.PLATFORM_TYPE.MacOS, injector, injectId, MAC_EXTERNAL_REFERENCE));
      actionMac.setAgents(agents);
      actions.add(actionMac);
    }
    return actions;
  }

  private String getUnixCommand(
      Endpoint.PLATFORM_TYPE platform,
      Injector injector,
      String injectId,
      String externalReferenceVariable) {
    String implantLocation =
        "location="
            + ExecutorHelper.IMPLANT_LOCATION_UNIX
            + ExecutorHelper.IMPLANT_BASE_NAME
            + UUID.randomUUID()
            + ";mkdir -p $location;filename=";
    // x86_64 by default in the register because CS API doesn't provide the platform architecture
    // (we update this when the download implant script is launched on the endpoint)
    String executorCommandKey = platform.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name();
    String command = injector.getExecutorCommands().get(executorCommandKey);
    // The default command to download the openaev implant and execute the attack is modified for CS
    // - UNIX_ARCH: CS doesn't know the endpoint architecture so we include it to get the
    // architecture before downloading the implant and we replace the default x86_64 put before
    // - externalReferenceVariable: the agent id in the openAEV DB for CS is the CS agent id to make
    // the batch attack works so we get it with a command line from the endpoint and give it to the
    // implant
    command =
        UNIX_ARCH
            + externalReferenceVariable
            + command.replace(Endpoint.PLATFORM_ARCH.x86_64.name(), ARCH_VARIABLE);
    command = replaceArgs(platform, command, injectId, AGENT_ID_VARIABLE);
    command =
        command.replaceFirst(
            "\\$?x=.+location=.+;filename=", Matcher.quoteReplacement(implantLocation));
    return Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8));
  }
}

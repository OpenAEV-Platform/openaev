package io.openaev.executors.mde.service;

import static io.openaev.executors.ExecutorHelper.*;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;
import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;

import io.openaev.config.OpenAEVConfig;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.BatchExecutorContextService;
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.mde.client.MdeExecutorClient;
import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.executors.mde.model.MdeAction;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Service(MdeExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class MdeExecutorContextService extends BatchExecutorContextService<MdeAction> {

  public static final String SERVICE_NAME = MDE_EXECUTOR_NAME;

  // MDE allows 10 Live Response calls per minute.
  private static final Duration BATCH_INTERVAL = Duration.ofMinutes(1);

  private final MdeExecutorConfig mdeExecutorConfig;
  private final MdeExecutorClient mdeExecutorClient;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ExecutorService executorService;
  private final OpenAEVConfig openAEVConfig;

  @Override
  public void launchExecutorSubprocess(
      @NotNull final Inject inject,
      @NotNull final Endpoint assetEndpoint,
      @NotNull final Agent agent,
      @NotNull final String token) {}

  @Override
  public List<Agent> launchBatchExecutorSubprocess(
      Inject inject, Set<Agent> agents, InjectStatus injectStatus, String token) {

    enterpriseEditionService.throwEEExecutorService(
        licenseCacheManager.getEnterpriseEditionInfo(), SERVICE_NAME, injectStatus);

    List<Agent> mdeAgents = new ArrayList<>(agents);

    // Sometimes assets from agents aren't fetched even with the EAGER property from Hibernate
    mdeAgents.forEach(agent -> agent.setAsset((Asset) Hibernate.unproxy(agent.getAsset())));

    mdeAgents = executorService.manageWithoutPlatformAgents(mdeAgents, injectStatus);

    Injector injector = inject.getInjector();
    if (injector == null) {
      injector =
          inject
              .getInjectorContract()
              .map(InjectorContract::getFirstInjector)
              .orElseThrow(
                  () -> new UnsupportedOperationException("Inject does not have a contract"));
    }

    List<MdeAction> actions = new ArrayList<>();
    actions.addAll(
        getWindowsActions(
            getAgentsFromOS(mdeAgents, Endpoint.PLATFORM_TYPE.Windows), injector, inject, token));
    actions.addAll(
        getLinuxActions(
            getAgentsFromOS(mdeAgents, Endpoint.PLATFORM_TYPE.Linux), injector, inject, token));
    actions.addAll(
        getMacOSActions(
            getAgentsFromOS(mdeAgents, Endpoint.PLATFORM_TYPE.MacOS), injector, inject, token));
    dispatch(actions);
    return mdeAgents;
  }

  @Override
  protected void send(MdeAction action) {
    mdeExecutorClient.executeAction(
        agentReference(action), action.getScriptName(), action.getCommandEncoded());
  }

  @Override
  protected String agentReference(MdeAction action) {
    return action.getAgent().getExternalReference();
  }

  @Override
  protected Integer batchSize() {
    return mdeExecutorConfig.getApiBatchExecutionActionPagination();
  }

  @Override
  protected int defaultBatchSize() {
    return MdeExecutorConfig.DEFAULT_API_BATCH_EXECUTION_ACTION_PAGINATION;
  }

  @Override
  protected Duration batchInterval() {
    return BATCH_INTERVAL;
  }

  private List<MdeAction> getWindowsActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    List<MdeAction> actions = new ArrayList<>();
    Endpoint.PLATFORM_TYPE platform = Endpoint.PLATFORM_TYPE.Windows;
    // Use the MDE-specific command (scheduled-task launch): MDE Live Response terminates the
    // session process tree on teardown, so the implant must run from a detached SYSTEM task to
    // survive and report its traces back to OpenAEV.
    String executorCommandKey =
        MDE_EXECUTOR_NAME + "." + platform.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name();
    String template = injector.getExecutorCommands().get(executorCommandKey);
    // One action per agent, mirroring CrowdStrike/SentinelOne: the implant is launched with the
    // OpenAEV agent id (its UUID primary key) as --agent-id so the execution callback resolves the
    // agent via findById, while MDE is targeted by the device id (external reference) in
    // send. Reading the device id from the endpoint registry at runtime is unreliable (it
    // is empty on some machines), and the agent primary key must not equal the external reference
    // (see migration Reassign_agent_ids_for_external_executors).
    for (Agent agent : agents) {
      MdeAction action = new MdeAction();
      action.setScriptName(mdeExecutorConfig.getWindowsScriptName());
      String implantLocation =
          "$location="
              + ExecutorHelper.IMPLANT_LOCATION_WINDOWS
              + ExecutorHelper.IMPLANT_BASE_NAME
              + UUID.randomUUID()
              + "\";md $location -ea 0;[Environment]::CurrentDirectory";
      // x86_64 by default — MDE API doesn't always expose architecture; the WINDOWS_ARCH snippet
      // detects it at runtime and replaces the value before downloading the implant.
      String command =
          WINDOWS_ARCH
              + template.replace(Endpoint.PLATFORM_ARCH.x86_64.name(), ARCH_VARIABLE + "`");
      command =
          replaceArgs(
              platform,
              command,
              inject.getId(),
              agent.getId(),
              inject.getTenant().getId(),
              token,
              openAEVConfig.getBaseUrlForAgent(),
              Integer.toString(openAEVConfig.getLogsMaxSize()),
              Boolean.toString(openAEVConfig.isUnsecuredCertificate()),
              Boolean.toString(openAEVConfig.isWithProxy()));
      command =
          command.replaceFirst(
              "\\$?x=.+location=.+;\\[Environment]::CurrentDirectory",
              Matcher.quoteReplacement(implantLocation));
      // Self-clean stale implant/payload dirs (>24h) at the start of every inject. This piggybacks
      // on the inject's own Live Response session, so cleanup happens only on agents that actually
      // run injects — replacing the periodic all-agents garbage collector that flooded the MDE API
      // and starved inject dispatch.
      command = WINDOWS_CLEAN_PAYLOADS_COMMAND + ";" + command;
      // MDE Live Response uses Invoke-Expression, not -EncodedCommand, so UTF-8 encoding is correct
      // (CrowdStrike RTR uses UTF-16LE for -encodedCommand, but that's not applicable here).
      action.setCommandEncoded(
          Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8)));
      action.setAgent(agent);
      actions.add(action);
    }
    return actions;
  }

  private List<MdeAction> getLinuxActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    return getUnixActions(agents, injector, inject, Endpoint.PLATFORM_TYPE.Linux, token);
  }

  private List<MdeAction> getMacOSActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    return getUnixActions(agents, injector, inject, Endpoint.PLATFORM_TYPE.MacOS, token);
  }

  private List<MdeAction> getUnixActions(
      List<Agent> agents,
      Injector injector,
      Inject inject,
      Endpoint.PLATFORM_TYPE platform,
      String token) {
    List<MdeAction> actions = new ArrayList<>();
    for (Agent agent : agents) {
      MdeAction action = new MdeAction();
      action.setScriptName(mdeExecutorConfig.getUnixScriptName());
      action.setCommandEncoded(getUnixCommand(platform, injector, inject, agent, token));
      action.setAgent(agent);
      actions.add(action);
    }
    return actions;
  }

  private String getUnixCommand(
      Endpoint.PLATFORM_TYPE platform,
      Injector injector,
      Inject inject,
      Agent agent,
      String token) {
    String implantLocation =
        "location="
            + ExecutorHelper.IMPLANT_LOCATION_UNIX
            + ExecutorHelper.IMPLANT_BASE_NAME
            + UUID.randomUUID()
            + ";mkdir -p $location;filename=";
    String executorCommandKey = platform.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name();
    String command = injector.getExecutorCommands().get(executorCommandKey);
    command = UNIX_ARCH + command.replace(Endpoint.PLATFORM_ARCH.x86_64.name(), ARCH_VARIABLE);
    command =
        replaceArgs(
            platform,
            command,
            inject.getId(),
            agent.getId(),
            inject.getTenant().getId(),
            token,
            openAEVConfig.getBaseUrlForAgent(),
            Integer.toString(openAEVConfig.getLogsMaxSize()),
            Boolean.toString(openAEVConfig.isUnsecuredCertificate()),
            Boolean.toString(openAEVConfig.isWithProxy()));
    command =
        command.replaceFirst(
            "\\$?x=.+location=.+;filename=", Matcher.quoteReplacement(implantLocation));
    // Self-clean stale implant/payload dirs (>24h) at the start of every inject (see Windows note).
    command = UNIX_CLEAN_PAYLOADS_COMMAND + ";" + command;
    return Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8));
  }
}

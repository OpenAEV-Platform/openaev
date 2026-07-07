package io.openaev.executors.mde.service;

import static io.openaev.executors.ExecutorHelper.*;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;
import static io.openaev.integration.impl.executors.mde.MdeExecutorIntegration.MDE_EXECUTOR_NAME;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorHelper;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.mde.client.MdeExecutorClient;
import io.openaev.executors.mde.config.MdeExecutorConfig;
import io.openaev.executors.mde.model.MdeAction;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Slf4j
@Service(MdeExecutorContextService.SERVICE_NAME)
@RequiredArgsConstructor
public class MdeExecutorContextService extends ExecutorContextService {

  public static final String SERVICE_NAME = MDE_EXECUTOR_NAME;

  private static final String AGENT_ID_VARIABLE = "$agentID";

  /**
   * Extracts the MDE Device ID on Windows from the WinDefend registry key. The DeviceId value
   * matches the {@code id} field returned by the MDE API.
   */
  private static final String WINDOWS_EXTERNAL_REFERENCE =
      "$agentID=(Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows Advanced Threat Protection\\Status').DeviceId -replace '-','';";

  /**
   * Extracts the MDE machine GUID on Linux. The machine_id file stores the UUID without hyphens,
   * matching the {@code id} field returned by the MDE API.
   */
  private static final String LINUX_EXTERNAL_REFERENCE =
      "agentID=$(cat /etc/mdatp/machine_id 2>/dev/null | tr -d '\\n' | tr -d '-' || mdatp health --field machine_guid 2>/dev/null | tr -d '\\n' | tr -d '-');";

  /** Extracts the MDE machine GUID on macOS via the mdatp health command. */
  private static final String MAC_EXTERNAL_REFERENCE =
      "agentID=$(mdatp health --field machine_guid 2>/dev/null | tr -d '\\n' | tr -d '-' || cat /Library/Application\\ Support/Microsoft/Defender/machine_id.txt 2>/dev/null | tr -d '\\n' | tr -d '-');";

  private final MdeExecutorConfig mdeExecutorConfig;
  private final MdeExecutorClient mdeExecutorClient;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;
  private final ExecutorService executorService;

  ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();

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
    executeActions(actions);
    return mdeAgents;
  }

  /**
   * Paginates MDE Live Response calls. MDE rate-limits the runliveresponse endpoint more strictly
   * than CrowdStrike RTR, so the default batch size is 10 machines per 5-second window.
   */
  public void executeActions(List<MdeAction> actions) {
    int paginationLimit = mdeExecutorConfig.getApiBatchExecutionActionPagination();
    for (MdeAction action : actions) {
      int paginationCount = (int) Math.ceil(action.getAgents().size() / (double) paginationLimit);
      for (int batchIndex = 0; batchIndex < paginationCount; batchIndex++) {
        int fromIndex = batchIndex * paginationLimit;
        int toIndex = Math.min(fromIndex + paginationLimit, action.getAgents().size());
        List<Agent> batchAgents = action.getAgents().subList(fromIndex, toIndex);
        scheduledExecutorService.schedule(
            () ->
                batchAgents.forEach(
                    agent ->
                        mdeExecutorClient.executeAction(
                            agent.getExternalReference(),
                            action.getScriptName(),
                            action.getCommandEncoded())),
            batchIndex * 5L,
            TimeUnit.SECONDS);
      }
    }
  }

  private List<MdeAction> getWindowsActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    List<MdeAction> actions = new ArrayList<>();
    if (!agents.isEmpty()) {
      MdeAction action = new MdeAction();
      action.setScriptName(mdeExecutorConfig.getWindowsScriptName());
      String implantLocation =
          "$location="
              + ExecutorHelper.IMPLANT_LOCATION_WINDOWS
              + ExecutorHelper.IMPLANT_BASE_NAME
              + UUID.randomUUID()
              + "\";md $location -ea 0;[Environment]::CurrentDirectory";
      Endpoint.PLATFORM_TYPE platform = Endpoint.PLATFORM_TYPE.Windows;
      // x86_64 by default — MDE API doesn't always expose architecture; the WINDOWS_ARCH snippet
      // detects it at runtime and replaces the value before downloading the implant.
      // Use the MDE-specific command (scheduled-task launch): MDE Live Response terminates the
      // session process tree on teardown, so the implant must run from a detached SYSTEM task to
      // survive and report its traces back to OpenAEV.
      String executorCommandKey =
          MDE_EXECUTOR_NAME + "." + platform.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name();
      String command = injector.getExecutorCommands().get(executorCommandKey);
      command =
          WINDOWS_ARCH
              + WINDOWS_EXTERNAL_REFERENCE
              + command.replace(Endpoint.PLATFORM_ARCH.x86_64.name(), ARCH_VARIABLE + "`");
      command =
          replaceArgs(
              platform,
              command,
              inject.getId(),
              AGENT_ID_VARIABLE,
              inject.getTenant().getId(),
              token);
      command =
          command.replaceFirst(
              "\\$?x=.+location=.+;\\[Environment]::CurrentDirectory",
              Matcher.quoteReplacement(implantLocation));
      // MDE Live Response uses Invoke-Expression, not -EncodedCommand, so UTF-8 encoding is correct
      // (CrowdStrike RTR uses UTF-16LE for -encodedCommand, but that's not applicable here).
      action.setCommandEncoded(
          Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8)));
      action.setAgents(agents);
      actions.add(action);
    }
    return actions;
  }

  private List<MdeAction> getLinuxActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    List<MdeAction> actions = new ArrayList<>();
    if (!agents.isEmpty()) {
      MdeAction action = new MdeAction();
      action.setScriptName(mdeExecutorConfig.getUnixScriptName());
      action.setCommandEncoded(
          getUnixCommand(
              Endpoint.PLATFORM_TYPE.Linux, injector, inject, LINUX_EXTERNAL_REFERENCE, token));
      action.setAgents(agents);
      actions.add(action);
    }
    return actions;
  }

  private List<MdeAction> getMacOSActions(
      List<Agent> agents, Injector injector, Inject inject, String token) {
    List<MdeAction> actions = new ArrayList<>();
    if (!agents.isEmpty()) {
      MdeAction action = new MdeAction();
      action.setScriptName(mdeExecutorConfig.getUnixScriptName());
      action.setCommandEncoded(
          getUnixCommand(
              Endpoint.PLATFORM_TYPE.MacOS, injector, inject, MAC_EXTERNAL_REFERENCE, token));
      action.setAgents(agents);
      actions.add(action);
    }
    return actions;
  }

  private String getUnixCommand(
      Endpoint.PLATFORM_TYPE platform,
      Injector injector,
      Inject inject,
      String externalReferenceVariable,
      String token) {
    String implantLocation =
        "location="
            + ExecutorHelper.IMPLANT_LOCATION_UNIX
            + ExecutorHelper.IMPLANT_BASE_NAME
            + UUID.randomUUID()
            + ";mkdir -p $location;filename=";
    String executorCommandKey = platform.name() + "." + Endpoint.PLATFORM_ARCH.x86_64.name();
    String command = injector.getExecutorCommands().get(executorCommandKey);
    command =
        UNIX_ARCH
            + externalReferenceVariable
            + command.replace(Endpoint.PLATFORM_ARCH.x86_64.name(), ARCH_VARIABLE);
    command =
        replaceArgs(
            platform,
            command,
            inject.getId(),
            AGENT_ID_VARIABLE,
            inject.getTenant().getId(),
            token);
    command =
        command.replaceFirst(
            "\\$?x=.+location=.+;filename=", Matcher.quoteReplacement(implantLocation));
    return Base64.getEncoder().encodeToString(command.getBytes(StandardCharsets.UTF_8));
  }
}

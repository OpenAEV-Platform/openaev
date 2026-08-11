package io.openaev.executors.sentinelone.service;

import static io.openaev.executors.ExecutorHelper.IMPLANT_BASE_NAME;
import static io.openaev.executors.utils.ExecutorUtils.getAgentsFromOS;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.executors.sentinelone.model.SentinelOneAction;
import io.openaev.service.AgentService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodically removes implant directories left behind by injects on SentinelOne endpoints.
 *
 * <p>Commands are SentinelOne-specific rather than the shared {@code ExecutorHelper} ones: they
 * only target {@code implant-*} directories so the agent's own content is preserved, and they never
 * emit errors nor a non-zero exit status, which SentinelOne reports as a failed task.
 */
@Slf4j
public class SentinelOneGarbageCollectorService implements Runnable {

  private static final String WINDOWS_AGENT_HOME = "C:\\Program Files (x86)\\Filigran\\OAEV Agent";
  private static final String UNIX_AGENT_HOME = "/opt/openaev-agent";
  private static final int IMPLANT_RETENTION_HOURS = 24;

  /**
   * No {@code -Recurse} on {@code Get-ChildItem}: implant directories are direct children, and
   * recursing would enumerate children already deleted by {@code Remove-Item}, raising errors.
   */
  public static final String WINDOWS_CLEAN_IMPLANTS_COMMAND =
      "Get-ChildItem -Path \""
          + WINDOWS_AGENT_HOME
          + "\\payloads\",\""
          + WINDOWS_AGENT_HOME
          + "\\runtimes\" -Directory -Filter \""
          + IMPLANT_BASE_NAME
          + "*\" -ErrorAction SilentlyContinue | Where-Object {$_.CreationTime -lt"
          + " (Get-Date).AddHours(-"
          + IMPLANT_RETENTION_HOURS
          + ")} | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue";

  /**
   * {@code -maxdepth 1} keeps {@code find} from descending into directories it just removed (which
   * makes it exit 1); {@code || true} guarantees a successful exit status.
   */
  public static final String UNIX_CLEAN_IMPLANTS_COMMAND =
      "find "
          + UNIX_AGENT_HOME
          + "/payloads "
          + UNIX_AGENT_HOME
          + "/runtimes -mindepth 1 -maxdepth 1 -type d -name '"
          + IMPLANT_BASE_NAME
          + "*' -mmin +"
          + (IMPLANT_RETENTION_HOURS * 60)
          + " -exec rm -rf {} + 2>/dev/null || true";

  private final SentinelOneExecutorConfig config;
  private final SentinelOneExecutorContextService sentinelOneExecutorContextService;
  private final AgentService agentService;
  private final String executorId;

  public SentinelOneGarbageCollectorService(
      SentinelOneExecutorConfig config,
      SentinelOneExecutorContextService sentinelOneExecutorContextService,
      AgentService agentService,
      String executorId) {
    this.config = config;
    this.sentinelOneExecutorContextService = sentinelOneExecutorContextService;
    this.agentService = agentService;
    this.executorId = executorId;
  }

  @Override
  public void run() {
    List<Agent> agents = this.agentService.getAgentsByExecutorId(executorId);
    if (!agents.isEmpty()) {
      List<SentinelOneAction> actions = new ArrayList<>();
      log.info("Running SentinelOne executor garbage collector on {} agents", agents.size());
      List<Agent> windowsAgents = getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Windows);
      for (Agent agent : windowsAgents) {
        SentinelOneAction action = new SentinelOneAction();
        action.setAgentExternalReference(agent.getExternalReference());
        action.setScriptId(this.config.getWindowsScriptId());
        action.setCommandEncoded(
            Base64.getEncoder()
                .encodeToString(
                    WINDOWS_CLEAN_IMPLANTS_COMMAND.getBytes(StandardCharsets.UTF_16LE)));
        actions.add(action);
      }
      List<Agent> unixAgents = new ArrayList<>();
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.Linux));
      unixAgents.addAll(getAgentsFromOS(agents, Endpoint.PLATFORM_TYPE.MacOS));
      for (Agent agent : unixAgents) {
        SentinelOneAction action = new SentinelOneAction();
        action.setAgentExternalReference(agent.getExternalReference());
        action.setScriptId(this.config.getUnixScriptId());
        action.setCommandEncoded(
            Base64.getEncoder()
                .encodeToString(UNIX_CLEAN_IMPLANTS_COMMAND.getBytes(StandardCharsets.UTF_8)));
        actions.add(action);
      }
      sentinelOneExecutorContextService.executeActions(actions);
    }
  }
}

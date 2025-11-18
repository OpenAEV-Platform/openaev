package io.openaev.executors.sentinelone.service;

import io.openaev.database.model.Agent;
import io.openaev.database.model.Endpoint;
import io.openaev.executors.sentinelone.client.SentinelOneExecutorClient;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.service.AgentService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SentinelOneGarbageCollectorService implements Runnable {
  // Clean payloads older than 24 hours
  private static final String WINDOWS_COMMAND_LINE =
      "Get-ChildItem -Path \"C:\\Program Files (x86)\\Filigran\\OAEV Agent\\payloads\",\"C:\\Program Files (x86)\\Filigran\\OAEV Agent\\runtimes\" -Directory -Recurse | Where-Object {$_.CreationTime -lt (Get-Date).AddHours(-24)} | Remove-Item -Recurse -Force";
  private static final String UNIX_COMMAND_LINE =
      "find /opt/openaev-agent/payloads /opt/openaev-agent/runtimes -type d -mmin +1440 -exec rm -rf {} + 2>/dev/null";
  private final SentinelOneExecutorConfig config;
  private final SentinelOneExecutorClient client;
  private final AgentService agentService;

  @Autowired
  public SentinelOneGarbageCollectorService(
      SentinelOneExecutorConfig config,
      SentinelOneExecutorClient client,
      AgentService agentService) {
    this.config = config;
    this.client = client;
    this.agentService = agentService;
  }

  @Override
  public void run() {
    log.info("Running SentinelOne executor garbage collector...");
    List<Agent> agents =
        this.agentService.getAgentsByExecutorType(
            SentinelOneExecutorService.SENTINELONE_EXECUTOR_TYPE);
    log.info("Running SentinelOne executor garbage collector on " + agents.size() + " agents");
    agents.forEach(
        agent -> {
          Endpoint endpoint = (Endpoint) agent.getAsset();
          switch (endpoint.getPlatform()) {
            case Windows -> {
              log.info("Sending Windows command line to " + endpoint.getName());
              this.client.executeScript(
                  List.of(agent.getExternalReference()),
                  this.config.getWindowsScriptId(),
                  Base64.getEncoder()
                      .encodeToString(WINDOWS_COMMAND_LINE.getBytes(StandardCharsets.UTF_16LE)));
            }
            case Linux, MacOS -> {
              log.info("Sending Unix command line to " + endpoint.getName());
              this.client.executeScript(
                  List.of(agent.getExternalReference()),
                  this.config.getUnixScriptId(),
                  Base64.getEncoder()
                      .encodeToString(UNIX_COMMAND_LINE.getBytes(StandardCharsets.UTF_8)));
            }
          }
        });
  }
}

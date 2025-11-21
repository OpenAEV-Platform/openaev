package io.openaev.executors.sentinelone.service;

import static io.openaev.executors.ExecutorHelper.UNIX_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.ExecutorHelper.WINDOWS_CLEAN_PAYLOADS_COMMAND;

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
                      .encodeToString(
                          WINDOWS_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_16LE)));
            }
            case Linux, MacOS -> {
              log.info("Sending Unix command line to " + endpoint.getName());
              this.client.executeScript(
                  List.of(agent.getExternalReference()),
                  this.config.getUnixScriptId(),
                  Base64.getEncoder()
                      .encodeToString(
                          UNIX_CLEAN_PAYLOADS_COMMAND.getBytes(StandardCharsets.UTF_8)));
            }
          }
        });
  }
}

package io.openaev.executors.tanium.service;

import static io.openaev.executors.ExecutorHelper.UNIX_CLEAN_PAYLOADS_COMMAND;
import static io.openaev.executors.ExecutorHelper.WINDOWS_CLEAN_PAYLOADS_COMMAND;

import io.openaev.database.model.Endpoint;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.service.AgentService;
import java.util.Base64;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TaniumGarbageCollectorService implements Runnable {

  private final TaniumExecutorConfig config;
  private final TaniumExecutorClient client;
  private final AgentService agentService;

  @Autowired
  public TaniumGarbageCollectorService(
      TaniumExecutorConfig config, TaniumExecutorClient client, AgentService agentService) {
    this.config = config;
    this.client = client;
    this.agentService = agentService;
  }

  @Override
  public void run() {
    log.info("Running Tanium executor garbage collector...");
    List<io.openaev.database.model.Agent> agents =
        this.agentService.getAgentsByExecutorType(TaniumExecutorService.TANIUM_EXECUTOR_TYPE);
    log.info("Running Tanium executor garbage collector on " + agents.size() + " agents");
    agents.forEach(
        agent -> {
          Endpoint endpoint = (Endpoint) agent.getAsset();
          switch (endpoint.getPlatform()) {
            case Windows -> {
              log.info("Sending Windows command line to " + endpoint.getName());
              this.client.executeAction(
                  agent.getExternalReference(),
                  this.config.getWindowsPackageId(),
                  Base64.getEncoder().encodeToString(WINDOWS_CLEAN_PAYLOADS_COMMAND.getBytes()));
            }
            case Linux, MacOS -> {
              log.info("Sending Unix command line to " + endpoint.getName());
              this.client.executeAction(
                  agent.getExternalReference(),
                  this.config.getUnixPackageId(),
                  Base64.getEncoder().encodeToString(UNIX_CLEAN_PAYLOADS_COMMAND.getBytes()));
            }
          }
        });
  }
}

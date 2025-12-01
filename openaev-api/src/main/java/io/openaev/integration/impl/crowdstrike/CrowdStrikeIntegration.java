package io.openaev.integration.impl.crowdstrike;

import io.openaev.executors.ExecutorService;
import io.openaev.executors.crowdstrike.client.CrowdStrikeExecutorClient;
import io.openaev.executors.crowdstrike.config.CrowdStrikeExecutorConfig;
import io.openaev.executors.crowdstrike.service.CrowdStrikeExecutorService;
import io.openaev.executors.crowdstrike.service.CrowdStrikeGarbageCollectorService;
import io.openaev.integration.Integration;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import lombok.RequiredArgsConstructor;

public class CrowdStrikeIntegration implements Integration {
  private final CrowdStrikeExecutorClient client;
  private final CrowdStrikeExecutorConfig config;
  private final EndpointService endpointService;
  private final AgentService agentService;
  private final AssetGroupService assetGroupService;
  private final ExecutorService executorService;

  private final CrowdStrikeExecutorService crowdStrikeExecutorService;
  private final CrowdStrikeGarbageCollectorService crowdStrikeGarbageCollectorService;

  public CrowdStrikeIntegration(CrowdStrikeExecutorClient client, CrowdStrikeExecutorConfig config, EndpointService endpointService, AgentService agentService, AssetGroupService assetGroupService, ExecutorService executorService) {
    this.client = client;
    this.config = config;
    this.endpointService = endpointService;
    this.agentService = agentService;
    this.assetGroupService = assetGroupService;
    this.executorService = executorService;

    crowdStrikeExecutorService = new CrowdStrikeExecutorService(executorService, client, config, endpointService, agentService, assetGroupService);
    crowdStrikeGarbageCollectorService = new CrowdStrikeGarbageCollectorService(config, client, agentService);
  }

  @Override
  public void start() {
    //executorService.register()
  }

  @Override
  public void stop() {

  }
}

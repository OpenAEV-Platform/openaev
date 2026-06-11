package io.openaev.utils.fixtures;

import static java.time.Instant.now;

import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetAgentJob;

public class AssetAgentJobFixture {

  public static AssetAgentJob createDefaultAssetAgentJob(Agent agent) {
    AssetAgentJob assetAgentJob = AssetAgentJob.fromTenant("tenant");
    assetAgentJob.setCommand("whoami");
    assetAgentJob.setAgent(agent);
    assetAgentJob.setCreatedAt(now());
    return assetAgentJob;
  }
}

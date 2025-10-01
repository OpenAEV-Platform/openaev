package io.openaev.utils.fixtures;

import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetAgentJob;

public class AssetAgentJobFixture {

  public static AssetAgentJob createDefaultAssetAgentJob(Agent agent) {
    AssetAgentJob assetAgentJob = new AssetAgentJob();
    assetAgentJob.setCommand("whoami");
    assetAgentJob.setAgent(agent);
    return assetAgentJob;
  }
}

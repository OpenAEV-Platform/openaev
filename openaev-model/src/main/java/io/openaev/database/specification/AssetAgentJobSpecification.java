package io.openaev.database.specification;

import io.openaev.database.model.AssetAgentJob;
import org.springframework.data.jpa.domain.Specification;

public class AssetAgentJobSpecification {

  public static Specification<AssetAgentJob> forEndpoint(
      String externalReference, String deploymentMode, String privilege, String executedByUser) {
    // TODO Add time limitation
    // TODO add cleanup
    return (root, query, cb) ->
        cb.and(
            cb.equal(root.get("agent").get("externalReference"), externalReference),
            cb.equal(root.get("agent").get("deploymentMode"), deploymentMode),
            cb.equal(root.get("agent").get("privilege"), privilege),
            cb.equal(root.get("agent").get("executedByUser"), executedByUser));
  }

  @Deprecated(since = "1.11.0")
  public static Specification<AssetAgentJob> forEndpoint(String externalReference) {
    return (root, query, cb) ->
        cb.equal(root.get("agent").get("externalReference"), externalReference);
  }
}

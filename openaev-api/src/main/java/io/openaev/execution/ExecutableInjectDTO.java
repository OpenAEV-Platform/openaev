package io.openaev.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.Injection;
import io.openaev.rest.asset.endpoint.output.EndpointTargetOutput;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExecutableInjectDTO {

  @JsonProperty("injection")
  private final Injection injection;

  @JsonProperty("assets")
  private final Set<EndpointTargetOutput> assets;

  @JsonProperty("assetGroups")
  private final Set<AssetGroupSimple> assetGroups;
}

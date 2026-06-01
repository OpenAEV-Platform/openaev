package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.ScopeAssetOutput;
import io.openaev.database.model.Asset;

/** Maps {@link Asset} entities to {@link ScopeAssetOutput} DTOs. */
public class ScopeAssetMapper {

  private ScopeAssetMapper() {}

  public static ScopeAssetOutput toOutput(Asset asset) {
    return new ScopeAssetOutput(
        asset.getId(), asset.getName(), asset.getType(), asset.getExternalReference());
  }
}

package io.openaev.utils.fixtures;

import io.openaev.database.model.Asset;
import java.time.Instant;
import org.jetbrains.annotations.NotNull;

public class AssetFixture {

  public static Asset createDefaultAsset(@NotNull final String name) {
    Asset asset = new Asset();
    asset.setCreatedAt(Instant.now());
    asset.setUpdatedAt(Instant.now());
    asset.setName(name);
    asset.setDescription("asset description");
    return asset;
  }
}

package io.openaev.api.asset;

import io.openaev.database.model.Asset;
import java.util.Arrays;
import java.util.Set;

public final class AssetMarkingIds {

  private AssetMarkingIds() {}

  public static Set<String> of(Asset asset) {
    String[] markingIds = asset.getMarkingIds();
    return markingIds == null ? Set.of() : Set.copyOf(Arrays.asList(markingIds));
  }
}

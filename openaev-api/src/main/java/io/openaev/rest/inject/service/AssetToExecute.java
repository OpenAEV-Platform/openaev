package io.openaev.rest.inject.service;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import java.util.ArrayList;
import java.util.List;

public record AssetToExecute(
    Asset asset, boolean isDirectlyLinkedToInject, List<AssetGroup> assetGroups) {

  public AssetToExecute(final Asset asset) {
    this(asset, true, new ArrayList<>());
  }
}

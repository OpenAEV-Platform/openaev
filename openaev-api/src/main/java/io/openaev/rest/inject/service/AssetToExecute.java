package io.openaev.rest.inject.service;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.CredentialSecretReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public record AssetToExecute(
    Asset asset, boolean isDirectlyLinkedToInject, List<AssetGroup> assetGroups) {

  public AssetToExecute(final Asset asset) {
    this(asset, true, new ArrayList<>());
  }

  /** The credentials of the AI targets among these targets. */
  public static Stream<CredentialSecretReference> aiTargetCredentials(
      List<AssetToExecute> targets) {
    return targets.stream()
        .map(AssetToExecute::asset)
        .map(Asset::getAiTargetCredential)
        .filter(Objects::nonNull);
  }
}

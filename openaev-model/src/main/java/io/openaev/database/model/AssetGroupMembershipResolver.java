package io.openaev.database.model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * Resolves the asset groups that contain given assets: static membership (the asset is listed in
 * the group) and dynamic membership (the group's filter matches the asset, within the group's
 * tenant).
 *
 * <p>The single source of truth for "the asset groups of a finding": a finding belongs to the
 * groups that contain at least one of its assets, never to the groups its inject targeted. The
 * search index ({@code base_asset_groups_side}) and the finding API both read it.
 */
public interface AssetGroupMembershipResolver {

  /**
   * @param assetIds the assets to resolve
   * @return for each asset id that belongs to at least one group, the ids of its groups; an asset
   *     in no group is absent from the map
   */
  Map<String, Set<String>> assetGroupIdsByAssetId(Collection<String> assetIds);
}

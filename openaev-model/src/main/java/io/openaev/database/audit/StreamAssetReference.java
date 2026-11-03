package io.openaev.database.audit;

import io.openaev.context.MarkingCtx;
import io.openaev.database.model.Asset;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The assets an entity's stream payload lists under one JSON property, each with its markings,
 * copied when the event is built: consumers run after commit, when the assets can no longer be
 * loaded. A reader receives only the assets their clearance allows.
 *
 * <p><b>Which kind to use.</b> The stream must show a reader what the REST API shows them, so an
 * entity follows the rule the SQL filtering applies to its table in {@code MarkingDerivedTables}.
 * The question is whether a reader who can see none of the linked assets should see the entity at
 * all:
 *
 * <ul>
 *   <li><b>{@link #about}</b>: the entity is a by-product of the assets, meaningless without them
 *       (a finding). In SQL its own table is filtered, {@code throughLinkRows("findings", …)}: it
 *       is hidden when it has assets and none is visible.
 *   <li><b>{@link #listing}</b>: the entity exists on its own, the assets are only configuration
 *       (an inject's targets, a group's members). In SQL only the link table is filtered, {@code
 *       linkedTo("injects_assets", …)}, {@code linkedTo("asset_groups_assets", …)}: the {@code
 *       injects} and {@code asset_groups} rows stay.
 *   <li><b>Belongs to one asset</b>: a single foreign key to the asset, {@code linkedTo("agents",
 *       "agent_asset", …)}, {@code linkedTo("injects_expectations", "asset_id", …)}. Not a
 *       reference: the entity overrides {@link io.openaev.database.model.Base#getStreamMarkings()}
 *       to return the asset's markings, and is hidden with it.
 * </ul>
 *
 * <p>An entity whose table is not in {@code MarkingDerivedTables} is not filtered in SQL, so it is
 * not filtered on the stream either. These overrides are written by hand: a change to {@code
 * MarkingDerivedTables} needs the matching change on the streamed entity, which {@code
 * MarkingDerivedTablesStreamTest} checks.
 *
 * @param property the JSON property listing the asset ids in the payload
 * @param markingsByAssetId the markings of each listed asset, empty for an unmarked one
 * @param hidesEntityWhenAllHidden whether the entity is about these assets, and hidden when they
 *     all are (a finding); otherwise it only lists them, and stays visible (an inject, an asset
 *     group)
 */
public record StreamAssetReference(
    String property, Map<String, Set<String>> markingsByAssetId, boolean hidesEntityWhenAllHidden) {

  public StreamAssetReference {
    markingsByAssetId = Map.copyOf(markingsByAssetId);
  }

  /** Assets an entity lists, which stays visible when they are all hidden. */
  public static StreamAssetReference listing(String property, Collection<? extends Asset> assets) {
    return new StreamAssetReference(property, markingsOf(assets), false);
  }

  /** Assets an entity is about, which is hidden when they are all hidden. */
  public static StreamAssetReference about(String property, Collection<? extends Asset> assets) {
    return new StreamAssetReference(property, markingsOf(assets), true);
  }

  private static Map<String, Set<String>> markingsOf(Collection<? extends Asset> assets) {
    return assets.stream()
        .filter(Objects::nonNull)
        .collect(
            Collectors.toMap(
                Asset::getId,
                asset -> BaseEvent.markingSet(asset.getMarkingIds()),
                (first, second) -> first));
  }

  /** The listed assets the clearance allows. */
  public Set<String> visibleAssetIds(MarkingCtx clearance) {
    return markingsByAssetId.entrySet().stream()
        .filter(asset -> clearance.allows(asset.getValue()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * Whether the entity is hidden under the clearance: it is about these assets and none is allowed.
   * An entity about no asset stays visible, like a finding with no asset in SQL.
   */
  public boolean hidesEntityUnder(MarkingCtx clearance) {
    return hidesEntityWhenAllHidden
        && !markingsByAssetId.isEmpty()
        && visibleAssetIds(clearance).isEmpty();
  }
}

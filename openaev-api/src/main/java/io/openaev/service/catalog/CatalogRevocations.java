package io.openaev.service.catalog;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toSet;

import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.catalog.CatalogItemStatus;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What the catalogue revokes: a version marked revoked, and every version of an item whose newest
 * version is, since the catalogue withdraws an item by releasing a revoked version of it.
 */
public record CatalogRevocations(
    Map<String, Set<String>> revokedVersions, Set<String> revokedItems) {

  public static <T extends CatalogItem> CatalogRevocations of(Collection<T> versions) {
    return new CatalogRevocations(
        versions.stream()
            .filter(CatalogRevocations::revoked)
            .collect(
                groupingBy(CatalogItem::getExternalId, mapping(CatalogItem::getVersion, toSet()))),
        versions.stream()
            .collect(
                Collectors.toMap(
                    CatalogItem::getExternalId, Function.identity(), CatalogListService::newer))
            .values()
            .stream()
            .filter(CatalogRevocations::revoked)
            .map(CatalogItem::getExternalId)
            .collect(toSet()));
  }

  public boolean revokes(String externalId, String version) {
    return revokedItems.contains(externalId)
        || revokedVersions.getOrDefault(externalId, Set.of()).contains(version);
  }

  /** The external IDs of the items the catalogue revokes a version of. */
  public Set<String> externalIds() {
    return Stream.concat(revokedItems.stream(), revokedVersions.keySet().stream()).collect(toSet());
  }

  private static boolean revoked(CatalogItem version) {
    return version.getStatus() == CatalogItemStatus.REVOKED;
  }
}

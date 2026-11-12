package io.openaev.service.catalog;

import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.api.catalog.dto.CatalogPayloadOutput;
import io.openaev.api.catalog.dto.CatalogScenarioOutput;
import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The catalogue of a tenant as the Available tabs show it: deprecated and revoked versions out. */
@Service
@RequiredArgsConstructor
public class CatalogListService {

  private final CatalogItemRepository catalogItemRepository;
  private final CatalogItemMapper catalogItemMapper;

  @Transactional(readOnly = true)
  public List<CatalogScenarioOutput> scenarios() {
    return latestVersions(catalogItemRepository.findAllScenarios()).stream()
        .map(catalogItemMapper::toOutput)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<CatalogPayloadOutput> payloads() {
    return latestVersions(catalogItemRepository.findAllPayloads()).stream()
        .map(catalogItemMapper::toOutput)
        .toList();
  }

  /** A deprecated or revoked version stays out of the catalogue; installed ones are untouched. */
  static boolean isOffered(CatalogItem item) {
    return item.getStatus().isOffered();
  }

  // The latest offered version of each item, sorted by name.
  private static <T extends CatalogItem> List<T> latestVersions(List<T> items) {
    return items.stream()
        .filter(CatalogListService::isOffered)
        .collect(
            Collectors.toMap(
                CatalogItem::getExternalId,
                Function.identity(),
                (a, b) ->
                    SemanticVersion.ORDER.compare(a.getVersion(), b.getVersion()) >= 0 ? a : b))
        .values()
        .stream()
        .sorted(Comparator.comparing(CatalogItem::getName, Comparator.nullsLast(String::compareTo)))
        .toList();
  }
}

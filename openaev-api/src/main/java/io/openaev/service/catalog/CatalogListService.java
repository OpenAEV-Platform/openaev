package io.openaev.service.catalog;

import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The catalogue of a tenant; deprecated and revoked versions are no longer offered. */
@Service
@RequiredArgsConstructor
public class CatalogListService {

  private final CatalogItemRepository catalogItemRepository;

  /** Every version of every catalogue item of a type, offered or not. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> all(Class<T> type) {
    return catalogItemRepository.findAllOfType(type);
  }

  /** Every offered version of every catalogue item of a type. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> allOffered(Class<T> type) {
    return offeredOfType(type);
  }

  /**
   * The latest offered version of every catalogue item of a type, sorted by name, but the items of
   * these keys: the ones the tenant installed, or ever knew.
   */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> latestOfferedExcept(Class<T> type, Set<String> keys) {
    return offeredOfType(type).stream()
        .collect(
            Collectors.toMap(
                CatalogItem::getExternalId,
                Function.identity(),
                (a, b) ->
                    SemanticVersion.ORDER.compare(a.getVersion(), b.getVersion()) >= 0 ? a : b))
        .values()
        .stream()
        .filter(item -> !keys.contains(CatalogOffer.key(item)))
        .sorted(Comparator.comparing(CatalogItem::getName, Comparator.nullsLast(String::compareTo)))
        .toList();
  }

  /** Every offered version of one catalogue item, newest first. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> offeredVersions(Class<T> type, String externalId) {
    return catalogItemRepository.findAllVersions(type, externalId).stream()
        .filter(CatalogListService::isOffered)
        .sorted(Comparator.comparing(CatalogItem::getVersion, SemanticVersion.ORDER).reversed())
        .toList();
  }

  /** A version the catalogue still offers; refused when it is unknown, deprecated or revoked. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> T offered(Class<T> type, String externalId, String version) {
    T item =
        catalogItemRepository
            .findVersion(type, externalId, version)
            .orElseThrow(() -> new ElementNotFoundException("Catalogue version not found"));
    if (!isOffered(item)) {
      throw new BadRequestException("This version is no longer offered");
    }
    return item;
  }

  private <T extends CatalogItem> List<T> offeredOfType(Class<T> type) {
    return catalogItemRepository.findAllOfType(type).stream()
        .filter(CatalogListService::isOffered)
        .toList();
  }

  private static boolean isOffered(CatalogItem item) {
    return item.getStatus().isOffered();
  }
}

package io.openaev.service.catalog;

import static io.openaev.utils.pagination.PaginationUtils.buildPageable;

import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.catalog.SemanticVersion;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The catalogue of a tenant as the Available tabs show it: deprecated and revoked versions out. */
@Service
@RequiredArgsConstructor
public class CatalogListService {

  private static final String ID = "id";

  private final CatalogItemRepository catalogItemRepository;

  // -- LIST --

  /** Every version of every catalogue item of a type, offered or not. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> all(Class<T> type) {
    return catalogItemRepository.findAllOfType(type);
  }

  /** Every offered version of every catalogue item of a type. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> allOffered(Class<T> type) {
    return catalogItemRepository.findAllOfType(type).stream()
        .filter(CatalogListService::isOffered)
        .toList();
  }

  /**
   * The latest offered version of every catalogue item of a type, sorted by name, but the items of
   * these keys: the ones the tenant installed, or ever knew.
   */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> latestOfferedExcept(Class<T> type, Set<String> keys) {
    return catalogItemRepository.findAllLatestOffered(type).stream()
        .filter(item -> !keys.contains(CatalogOffer.key(item)))
        .toList();
  }

  /** Every released version of one catalogue item, offered or not, newest first. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> versions(Class<T> type, String externalId) {
    return newestFirst(catalogItemRepository.findAllVersions(type, externalId));
  }

  /** Every released version of these catalogue items, in no order. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> versions(Class<T> type, Collection<String> externalIds) {
    return catalogItemRepository.findAllVersionsIn(type, externalIds);
  }

  /** Every offered version of one catalogue item, newest first. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> offeredVersions(Class<T> type, String externalId) {
    return newestFirst(catalogItemRepository.findAllVersions(type, externalId)).stream()
        .filter(CatalogListService::isOffered)
        .toList();
  }

  private static <T extends CatalogItem> List<T> newestFirst(List<T> versions) {
    return versions.stream()
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

  /** One version of a catalogue item, whatever its status. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> Optional<T> version(
      Class<T> type, String externalId, String version) {
    return catalogItemRepository.findVersion(type, externalId, version);
  }

  // -- SEARCH --

  /** A page of the items an Available tab lists, under the filters and search of the input. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> Page<T> search(
      Class<T> type,
      Specification<CatalogItem> listed,
      SearchPaginationInput input,
      List<CatalogFacet> facets) {
    Pageable pageable = buildPageable(input, CatalogItem.class);
    // Items of one release share their publication date: the ID keeps the pages apart.
    Pageable stable =
        PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by(ID)));
    return catalogItemRepository
        .findAll(listed.and(CatalogFacet.matching(input, facets)), stable)
        .map(type::cast);
  }

  // -- STEPS --

  /** A deprecated or revoked version stays out of the catalogue; installed ones are untouched. */
  static boolean isOffered(CatalogItem item) {
    return item.getStatus().isOffered();
  }

  // The latest offered version of each item, sorted by name.
  public static <T extends CatalogItem> List<T> latestVersions(List<T> items) {
    return items.stream()
        .filter(CatalogListService::isOffered)
        .collect(
            Collectors.toMap(
                CatalogItem::getExternalId, Function.identity(), CatalogListService::newer))
        .values()
        .stream()
        .sorted(Comparator.comparing(CatalogItem::getName, Comparator.nullsLast(String::compareTo)))
        .toList();
  }

  static <T extends CatalogItem> T newer(T a, T b) {
    return SemanticVersion.ORDER.compare(a.getVersion(), b.getVersion()) >= 0 ? a : b;
  }
}

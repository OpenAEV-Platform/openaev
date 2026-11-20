package io.openaev.service.catalog;

import static io.openaev.utils.FilterUtilsJpa.computeFilterGroupJpa;
import static io.openaev.utils.pagination.PaginationUtils.buildPageable;
import static io.openaev.utils.pagination.SearchPaginationInputMapper.withoutFilter;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

import io.openaev.database.model.Filters;
import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.schema.SchemaUtils;
import io.openaev.utils.catalog.SemanticVersion;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final EntityManager entityManager;

  // -- LIST --

  /** The latest offered version of every catalogue item of a type, sorted by name. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> List<T> latestOffered(Class<T> type) {
    return catalogItemRepository.findAllLatestOffered(type);
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

  // -- SEARCH --

  /** A page of the items an Available tab lists, under the filters and search of the input. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> Page<T> search(
      Class<T> type, Specification<CatalogItem> listed, SearchPaginationInput input) {
    Pageable pageable = buildPageable(input, CatalogItem.class);
    // Items of one release share their publication date: the ID keeps the pages apart.
    Pageable stable =
        PageRequest.of(
            pageable.getPageNumber(), pageable.getPageSize(), pageable.getSort().and(Sort.by(ID)));
    return catalogItemRepository.findAll(listed.and(matching(input)), stable).map(type::cast);
  }

  /** The number of listed items per value of each facet, under the other filters of the input. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> Map<String, Map<String, Long>> facetCounts(
      Class<T> type,
      Specification<CatalogItem> listed,
      SearchPaginationInput input,
      List<CatalogFacet> facets) {
    Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
    for (CatalogFacet facet : facets) {
      Specification<CatalogItem> others = listed.and(matching(withoutFilter(input, facet.key())));
      counts.put(
          facet.key(),
          facet.values().isEmpty()
              ? countByValue(type, others, facet.key())
              : countEach(others, facet));
    }
    return counts;
  }

  // -- UPDATE --

  /** Marks the latest offered version of each item of a repository, after a release. */
  @Transactional(rollbackFor = Exception.class)
  public void markLatestOffered(String repository) {
    List<CatalogItem> items = catalogItemRepository.findAllByReleaseRepository(repository);
    Set<String> latest =
        items.stream().collect(groupingBy(CatalogItem::getType)).values().stream()
            .flatMap(sameType -> latestVersions(sameType).stream())
            .map(CatalogItem::getId)
            .collect(toSet());
    items.forEach(item -> item.setLatestOffered(latest.contains(item.getId())));
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
                CatalogItem::getExternalId,
                Function.identity(),
                (a, b) ->
                    SemanticVersion.ORDER.compare(a.getVersion(), b.getVersion()) >= 0 ? a : b))
        .values()
        .stream()
        .sorted(Comparator.comparing(CatalogItem::getName, Comparator.nullsLast(String::compareTo)))
        .toList();
  }

  private static Specification<CatalogItem> matching(SearchPaginationInput input) {
    Specification<CatalogItem> filters = computeFilterGroupJpa(input.getFilterGroup());
    return filters.and(computeSearchJpa(input.getTextSearch()));
  }

  // One grouped query: the values come from the items.
  private <T extends CatalogItem> Map<String, Long> countByValue(
      Class<T> type, Specification<CatalogItem> listed, String key) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<T> root = query.from(type);
    @SuppressWarnings("unchecked")
    Root<CatalogItem> item = (Root<CatalogItem>) (Root<?>) root;
    Path<String> value =
        root.get(SchemaUtils.retrieveProperty(SchemaUtils.schema(type), key).getName());
    query
        .multiselect(value, cb.count(root))
        .where(listed.toPredicate(item, query, cb), cb.isNotNull(value))
        .groupBy(value);
    Map<String, Long> counts = new LinkedHashMap<>();
    entityManager
        .createQuery(query)
        .getResultList()
        .forEach(row -> counts.put(row.get(0, String.class), row.get(1, Long.class)));
    return counts;
  }

  // A list column cannot be grouped: one count per value, with the filter a facet row sets.
  private Map<String, Long> countEach(Specification<CatalogItem> listed, CatalogFacet facet) {
    Map<String, Long> counts = new LinkedHashMap<>();
    for (String value : facet.values()) {
      Filters.FilterGroup only =
          Filters.FilterGroup.builder()
              .filters(
                  List.of(
                      Filters.Filter.builder()
                          .id(facet.key())
                          .key(facet.key())
                          .values(List.of(value))
                          .build()))
              .build();
      counts.put(value, catalogItemRepository.count(listed.and(computeFilterGroupJpa(only))));
    }
    return counts;
  }
}

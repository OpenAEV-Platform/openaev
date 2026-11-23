package io.openaev.service.catalog;

import static io.openaev.utils.pagination.SearchPaginationInputMapper.withoutFilter;

import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.repository.CatalogItemRepository;
import io.openaev.schema.SchemaUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Counts the facets of an Available tab in the database. */
@Service
@RequiredArgsConstructor
public class CatalogFacetCounter {

  private final CatalogItemRepository catalogItemRepository;
  private final EntityManager entityManager;

  /** The number of listed items per value of each facet, under the other filters of the input. */
  @Transactional(readOnly = true)
  public <T extends CatalogItem> Map<String, Map<String, Long>> count(
      Class<T> type,
      Specification<CatalogItem> listed,
      SearchPaginationInput input,
      List<CatalogFacet> facets) {
    Map<String, Map<String, Long>> counts = new LinkedHashMap<>();
    for (CatalogFacet facet : facets) {
      Specification<CatalogItem> others =
          listed.and(CatalogFacet.matching(withoutFilter(input, facet.key()), facets));
      counts.put(facet.key(), facet.count(this, type, others));
    }
    return counts;
  }

  long count(Specification<CatalogItem> listed) {
    return catalogItemRepository.count(listed);
  }

  <T extends CatalogItem> Map<String, Long> countByValue(
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
}

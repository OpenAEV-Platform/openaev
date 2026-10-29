package io.openaev.search;

import static io.openaev.search.SearchableClass.ID;
import static io.openaev.search.SearchableClass.TAGS;
import static io.openaev.utils.pagination.SortUtilsCriteriaBuilder.toSortCriteriaBuilder;

import io.openaev.database.model.Base;
import io.openaev.database.model.Tag;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import jakarta.persistence.metamodel.EntityType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

// Projection: entities are never loaded, so their eager associations and graphs are skipped.
@Component
@RequiredArgsConstructor
public class FullTextSearchQuery {

  private static final String ID_ALIAS = "id";
  private static final String NAME_ALIAS = "name";
  private static final String DESCRIPTION_ALIAS = "description";
  private static final String TAGS_ALIAS = "tags";
  private static final String TYPE_ALIAS = "type";

  private final EntityManager entityManager;

  /** Fail at startup if a searched or displayed attribute does not exist on its entity. */
  void validateAttributes(SearchableClass<?> searchable) {
    EntityType<?> entityType = entityManager.getMetamodel().entity(searchable.clazz());
    Stream.of(ID, TAGS, searchable.nameAttribute(), searchable.descriptionAttribute())
        .filter(Objects::nonNull)
        .forEach(entityType::getAttribute);
  }

  <T extends Base> Page<FullTextSearchResult> findResults(
      SearchableClass<T> searchable, Specification<T> specification, Pageable pageable) {
    Class<T> clazz = searchable.clazz();
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> cq = cb.createTupleQuery();
    Root<T> root = cq.from(clazz);

    String descriptionAttribute = searchable.descriptionAttribute();

    List<Selection<?>> selections = new ArrayList<>();
    selections.add(root.get(ID).alias(ID_ALIAS));
    selections.add(root.get(searchable.nameAttribute()).alias(NAME_ALIAS));
    selections.add(
        (descriptionAttribute == null
                ? cb.nullLiteral(String.class)
                : root.<String>get(descriptionAttribute))
            .alias(DESCRIPTION_ALIAS));
    selections.add(tagIds(cb, root).alias(TAGS_ALIAS));
    selections.add(root.type().alias(TYPE_ALIAS));
    cq.multiselect(selections).groupBy(root.get(ID));

    Predicate predicate = specification.toPredicate(root, cq, cb);
    if (predicate != null) {
      cq.where(predicate);
    }
    // Keep the rank order set by the full text specification unless a sort is requested
    if (pageable.getSort().isSorted()) {
      cq.orderBy(toSortCriteriaBuilder(cb, root, pageable.getSort()));
    }

    List<FullTextSearchResult> results =
        entityManager
            .createQuery(cq)
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize())
            .getResultList()
            .stream()
            .map(
                tuple ->
                    new FullTextSearchResult(
                        tuple.get(ID_ALIAS, String.class),
                        tuple.get(NAME_ALIAS, String.class),
                        tuple.get(DESCRIPTION_ALIAS, String.class),
                        Set.copyOf(Arrays.asList(tuple.get(TAGS_ALIAS, String[].class))),
                        searchable.resultClass(tuple.get(TYPE_ALIAS, Class.class))))
            .toList();

    return new PageImpl<>(results, pageable, count(clazz, specification));
  }

  // Tag ids of each row, empty instead of [null] when it has no tag
  private static <T> Expression<String[]> tagIds(CriteriaBuilder cb, Root<T> root) {
    HibernateCriteriaBuilder hcb = (HibernateCriteriaBuilder) cb;
    Join<T, Tag> tagsJoin = root.join(TAGS, JoinType.LEFT);
    Expression<String[]> tagIdsWithNulls = hcb.arrayAgg(null, tagsJoin.get(ID));
    Expression<String> nullString = cb.nullLiteral(String.class);
    return hcb.arrayRemove(tagIdsWithNulls, nullString);
  }

  private <T> long count(Class<T> clazz, Specification<T> specification) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> cq = cb.createQuery(Long.class);
    Root<T> root = cq.from(clazz);
    cq.select(cb.countDistinct(root));
    Predicate predicate = specification.toPredicate(root, cq, cb);
    if (predicate != null) {
      cq.where(predicate);
    }
    // The full text specification adds a rank order, invalid in a count
    cq.orderBy(List.of());
    return entityManager.createQuery(cq).getSingleResult();
  }
}

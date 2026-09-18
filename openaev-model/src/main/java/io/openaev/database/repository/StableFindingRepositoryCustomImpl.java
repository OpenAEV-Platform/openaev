package io.openaev.database.repository;

import io.openaev.database.model.Injector;
import io.openaev.database.model.StableFinding;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

@RequiredArgsConstructor
public class StableFindingRepositoryCustomImpl implements StableFindingRepositoryCustom {

  private final EntityManager entityManager;

  @Override
  public Map<String, Long> countByType(Specification<StableFinding> specification) {
    return countByProperty(specification, "type");
  }

  @Override
  public Map<String, Long> countBySeverity(Specification<StableFinding> specification) {
    return countByProperty(specification, "latestSeverity");
  }

  @Override
  public Map<String, Long> countByCloudProvider(Specification<StableFinding> specification) {
    return countByProperty(specification, "cloudProvider");
  }

  @Override
  public Map<String, SourceCount> countBySource(Specification<StableFinding> specification) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<StableFinding> root = query.from(StableFinding.class);
    Join<StableFinding, Injector> source = root.join("sourceInjector", JoinType.LEFT);
    Expression<Long> count = cb.countDistinct(root.get("id"));
    query.multiselect(
        source.get("id").alias("id"),
        source.get("name").alias("name"),
        source.get("type").alias("type"),
        count.alias("count"));
    applySpecification(specification, root, query, cb);
    query.groupBy(source.get("id"), source.get("name"), source.get("type"));

    Map<String, SourceCount> counts = new LinkedHashMap<>();
    entityManager.createQuery(query).getResultList().stream()
        .filter(tuple -> tuple.get("id", String.class) != null)
        .forEach(
            tuple -> {
              String id = tuple.get("id", String.class);
              counts.put(
                  id,
                  new SourceCount(
                      id,
                      tuple.get("name", String.class),
                      tuple.get("type", String.class),
                      tuple.get("count", Long.class)));
            });
    return counts;
  }

  private Map<String, Long> countByProperty(
      Specification<StableFinding> specification, String property) {
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Tuple> query = cb.createTupleQuery();
    Root<StableFinding> root = query.from(StableFinding.class);
    Expression<?> value = root.get(property);
    query.multiselect(value.alias("value"), cb.countDistinct(root.get("id")).alias("count"));
    applySpecification(specification, root, query, cb);
    query.groupBy(value);

    Map<String, Long> counts = new LinkedHashMap<>();
    entityManager.createQuery(query).getResultList().stream()
        .filter(tuple -> tuple.get("value") != null)
        .forEach(
            tuple -> counts.put(tuple.get("value").toString(), tuple.get("count", Long.class)));
    return counts;
  }

  private void applySpecification(
      Specification<StableFinding> specification,
      Root<StableFinding> root,
      CriteriaQuery<?> query,
      CriteriaBuilder cb) {
    Predicate predicate = specification.toPredicate(root, query, cb);
    if (predicate != null) {
      query.where(predicate);
    }
  }
}

package io.openaev.service.targets.search.specifications;

import static io.openaev.service.targets.search.specifications.SearchSpecificationUtils.createJoinedFrom;

import io.openaev.database.model.Inject;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IncludeDirectEndpointTargetsSpecification<T> {

  public Specification<T> buildSpecification(Inject scopedInject, List<String> joinPath) {
    return getDirectTargetingSpecification(scopedInject, joinPath);
  }

  private Specification<T> getDirectTargetingSpecification(
      Inject scopedInject, List<String> joinPath) {
    return (root, query, criteriaBuilder) -> {
      Subquery<Integer> subQuery = query.subquery(Integer.class);
      Root<Inject> injectTable = subQuery.from(Inject.class);
      From<?, ?> finalFrom = createJoinedFrom(injectTable, joinPath);

      subQuery
          .select(criteriaBuilder.literal(1))
          .where(
              criteriaBuilder.equal(injectTable.get("id"), scopedInject.getId()),
              criteriaBuilder.equal(finalFrom.get("id"), root.get("id")));
      return criteriaBuilder.exists(subQuery);
    };
  }
}

package io.openaev.utils.pagination;

import static io.openaev.schema.SchemaUtils.getSearchableProperties;
import static io.openaev.utils.JpaUtils.toPath;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.schema.PropertySchema;
import io.openaev.schema.SchemaUtils;
import io.openaev.utils.OperationUtilsJpa;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.data.jpa.domain.Specification;

public class SearchUtilsJpa {

  private SearchUtilsJpa() {}

  private static final Specification<?> EMPTY_SPECIFICATION = (root, query, cb) -> cb.conjunction();

  @SuppressWarnings("unchecked")
  public static <T> Specification<T> computeSearchJpa(@Nullable final String search) {

    if (!hasText(search)) {
      return (Specification<T>) EMPTY_SPECIFICATION;
    }

    return (root, query, cb) -> {
      List<PropertySchema> propertySchemas = SchemaUtils.schema(root.getJavaType());
      List<PropertySchema> searchableProperties = getSearchableProperties(propertySchemas);
      List<Predicate> predicates =
          searchableProperties.stream()
              .map(
                  propertySchema -> {
                    Expression<String> paths = toPath(propertySchema, root, new HashMap<>());
                    return toPredicate(paths, search, cb, propertySchema.getType());
                  })
              .toList();
      return cb.or(predicates.toArray(Predicate[]::new));
    };
  }

  private static Predicate toPredicate(
      @NotNull final Expression<String> paths,
      @NotNull final String search,
      @NotNull final CriteriaBuilder cb,
      @NotNull final Class<?> type) {
    return OperationUtilsJpa.containsText(paths, cb, search, type);
  }
}

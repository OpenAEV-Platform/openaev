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
import java.util.ArrayList;
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
      List<PropertySchema> propertySchemas;
      try {
        propertySchemas = SchemaUtils.schemaWithSubtypes(root.getJavaType());
      } catch (ClassNotFoundException e) {
        throw new RuntimeException("Error loading schema for search", e);
      }

      List<PropertySchema> searchableProperties = getSearchableProperties(propertySchemas);
      List<Predicate> predicates = new ArrayList<>();

      for (PropertySchema propertySchema : searchableProperties) {
        if (propertySchema.getPaths() != null && propertySchema.getPaths().length > 0) {
          List<Predicate> multiPathPredicates = new ArrayList<>();
          for (String path : propertySchema.getPaths()) {
            PropertySchema singlePathPropertySchema =
                PropertySchema.builder()
                    .name(propertySchema.getName())
                    .type(propertySchema.getType())
                    .path(path)
                    .build();

            Expression<String> expression = toPath(singlePathPropertySchema, root, new HashMap<>());
            multiPathPredicates.add(toPredicate(expression, search, cb, propertySchema.getType()));
          }
          predicates.add(cb.or(multiPathPredicates.toArray(Predicate[]::new)));

        } else {
          Expression<String> expression = toPath(propertySchema, root, new HashMap<>());
          predicates.add(toPredicate(expression, search, cb, propertySchema.getType()));
        }
      }
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

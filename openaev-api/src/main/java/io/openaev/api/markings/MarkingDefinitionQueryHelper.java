package io.openaev.api.markings;

import static io.openaev.api.markings.response.MarkingDefinitionOutput.*;

import io.openaev.api.markings.response.MarkingDefinitionOutput;
import io.openaev.database.model.MarkingDefinition;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;

public class MarkingDefinitionQueryHelper {

  private MarkingDefinitionQueryHelper() {}

  // -- SELECT --
  public static void select(CriteriaQuery<Tuple> cq, Root<MarkingDefinition> root) {
    cq.multiselect(
            root.get("id").alias(ALIAS_ID),
            root.get("type").alias(ALIAS_TYPE),
            root.get("name").alias(ALIAS_NAME),
            root.get("order").alias(ALIAS_ORDER),
            root.get("color").alias(ALIAS_COLOR))
        .distinct(true);
  }

  // -- EXECUTION --
  public static List<MarkingDefinitionOutput> execution(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                new MarkingDefinitionOutput(
                    tuple.get(ALIAS_ID, String.class),
                    tuple.get(ALIAS_TYPE, String.class),
                    tuple.get(ALIAS_NAME, String.class),
                    tuple.get(ALIAS_ORDER, Integer.class),
                    tuple.get(ALIAS_COLOR, String.class)))
        .toList();
  }
}

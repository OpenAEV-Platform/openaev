package io.openaev.api.platform.groups;

import static io.openaev.api.platform.groups.PlatformGroupOutput.*;

import io.openaev.database.model.PlatformGroup;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.List;

public class PlatformGroupQueryHelper {

  private PlatformGroupQueryHelper() {}

  // -- SELECT --
  public static void select(CriteriaQuery<Tuple> cq, Root<PlatformGroup> root) {
    cq.multiselect(
            root.get("id").alias(ALIAS_ID),
            root.get("name").alias(ALIAS_NAME),
            root.get("description").alias(ALIAS_DESCRIPTION))
        .distinct(true);
  }

  // -- EXECUTION --
  public static List<PlatformGroupOutput> execution(TypedQuery<Tuple> query) {
    return query.getResultList().stream()
        .map(
            tuple ->
                new PlatformGroupOutput(
                    tuple.get(ALIAS_ID, String.class),
                    tuple.get(ALIAS_NAME, String.class),
                    tuple.get(ALIAS_DESCRIPTION, String.class)))
        .toList();
  }
}

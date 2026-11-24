package io.openaev.service.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.service.stream.StreamMarkingRules.EntityRule;
import io.openaev.service.stream.StreamMarkingRules.RowSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

/**
 * Computes, once per event and after commit, what it shows each reader ({@link StreamVisibility}):
 * the rules of its entity ({@link StreamMarkingRules}) applied to its payload, with the markings of
 * the rows it points to read by id at system clearance.
 */
@Service
@RequiredArgsConstructor
public class StreamVisibilityService {

  /** A chain longer than this is a registry mistake, not a real hierarchy. */
  private static final int MAX_CHAIN = 5;

  @PersistenceContext private EntityManager entityManager;

  private final StreamMarkingRules rules;
  private final TenantScopedTransaction tenantScopedTransaction;
  private final ObjectMapper mapper;

  /**
   * @param tenantId the tenant of the event's entity: the rows it points to are read there, at
   *     system clearance. With none, they cannot be read, and the event fails closed.
   */
  public StreamVisibility visibilityOf(BaseEvent event, String tenantId) {
    EntityRule rule = rules.forEntity(Hibernate.getClassLazy(event.getInstance()));
    if (rule.isEmpty()) {
      return StreamVisibility.UNFILTERED;
    }
    ObjectNode payload = mapper.valueToTree(event.getInstance());
    Map<String, Set<String>> referenced =
        new StreamVisibility(rules, rule, payload, Map.of()).referencedIds();
    Map<String, Map<String, Set<String>>> markings =
        referenced.isEmpty() || tenantId == null ? Map.of() : read(referenced, tenantId);
    return new StreamVisibility(rules, rule, payload, markings);
  }

  private Map<String, Map<String, Set<String>>> read(
      Map<String, Set<String>> referenced, String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantScopedTransaction.execute(
          TxCtx.forTenant(tenantId), () -> readMarkings(referenced));
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  /** The markings of the referenced rows found, by table and id, in the current transaction. */
  Map<String, Map<String, Set<String>>> readMarkings(Map<String, Set<String>> referenced) {
    Map<String, Map<String, Set<String>>> markings = new HashMap<>();
    referenced.forEach((table, ids) -> markings.put(table, markingsOf(table, ids, 0)));
    return markings;
  }

  /** The markings of each row found, by id; a row of a derived table carries its parent's. */
  private Map<String, Set<String>> markingsOf(String table, Set<String> ids, int depth) {
    Optional<RowSource> source = rules.sourceOf(table);
    if (source.isEmpty() || ids.isEmpty() || depth > MAX_CHAIN) {
      return Map.of();
    }
    RowSource row = source.get();
    if (row.markingsAttribute() != null) {
      return select(
              "select e.%s, e.%s from %s e where e.%s in :ids"
                  .formatted(
                      row.idAttribute(),
                      row.markingsAttribute(),
                      row.entityName(),
                      row.idAttribute()),
              ids)
          .stream()
          .collect(
              Collectors.toMap(
                  values -> (String) values[0], values -> markingSet((String[]) values[1])));
    }
    List<Object[]> parents =
        select(
            "select e.%s, p.%s from %s e left join e.%s p where e.%s in :ids"
                .formatted(
                    row.idAttribute(),
                    row.parentIdAttribute(),
                    row.entityName(),
                    row.parentAttribute(),
                    row.idAttribute()),
            ids);
    Map<String, Set<String>> parentMarkings =
        markingsOf(
            row.parentTable(),
            parents.stream()
                .map(values -> (String) values[1])
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()),
            depth + 1);
    Map<String, Set<String>> markings = new HashMap<>();
    for (Object[] values : parents) {
      String parentId = (String) values[1];
      // No parent: like a NULL foreign key in SQL, visible. A parent not found stays unresolved.
      Set<String> found = parentId == null ? Set.of() : parentMarkings.get(parentId);
      if (found != null) {
        markings.put((String) values[0], found);
      }
    }
    return markings;
  }

  // The JPQL is built from the mapping and the registry, never from input.
  private List<Object[]> select(String jpql, Set<String> ids) {
    return entityManager.createQuery(jpql, Object[].class).setParameter("ids", ids).getResultList();
  }

  private static Set<String> markingSet(String[] markingIds) {
    return markingIds == null
        ? Set.of()
        : Arrays.stream(markingIds).filter(Objects::nonNull).collect(Collectors.toSet());
  }
}

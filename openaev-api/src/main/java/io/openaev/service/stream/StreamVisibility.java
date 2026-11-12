package io.openaev.service.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.context.MarkingCtx;
import io.openaev.service.stream.StreamMarkingRules.EmbeddedRule;
import io.openaev.service.stream.StreamMarkingRules.EntityRule;
import io.openaev.service.stream.StreamMarkingRules.ListingRule;
import io.openaev.service.stream.StreamMarkingRules.RowRule;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * What one event shows each reader, by clearance: built once per event, after commit, from its
 * payload and the markings of the rows it points to ({@link StreamVisibilityService}). A row whose
 * markings could not be read is visible only to a reader who sees everything: the stream fails
 * closed.
 */
public final class StreamVisibility {

  /** An event the marking rules do not apply to: sent to every reader as it is. */
  public static final StreamVisibility UNFILTERED = new StreamVisibility(null, null, null, null);

  /** Stands for markings that could not be read: no clearance but the unrestricted one holds it. */
  private static final Set<String> UNRESOLVED = Set.of("unresolved-marking");

  private final StreamMarkingRules rules;
  private final EntityRule rule;
  private final ObjectNode payload;
  private final Map<String, Map<String, Set<String>>> markingsByTable;

  StreamVisibility(
      StreamMarkingRules rules,
      EntityRule rule,
      ObjectNode payload,
      Map<String, Map<String, Set<String>>> markingsByTable) {
    this.rules = rules;
    this.rule = rule;
    this.payload = payload;
    this.markingsByTable = markingsByTable;
  }

  /** An event whose visibility could not be computed: sent only to unrestricted readers. */
  public static StreamVisibility unresolved() {
    return new StreamVisibility(null, null, null, Map.of());
  }

  public boolean isUnfiltered() {
    return markingsByTable == null;
  }

  public boolean visibleUnder(MarkingCtx clearance) {
    if (isUnfiltered() || clearance instanceof MarkingCtx.All) {
      return true;
    }
    return rule != null && rowVisible(rule.row(), payload, clearance);
  }

  /** Whether the payload lists or embeds rows to remove for a restricted reader. */
  public boolean filtersPayload() {
    return !isUnfiltered()
        && rule != null
        && (!rule.listings().isEmpty() || !rule.embedded().isEmpty());
  }

  /** The payload without the listed ids and embedded objects the clearance does not allow. */
  public ObjectNode payloadFor(MarkingCtx clearance) {
    ObjectNode filtered = payload.deepCopy();
    for (ListingRule listing : rule.listings()) {
      if (filtered.get(listing.property()) instanceof ArrayNode ids) {
        ArrayNode visible = filtered.putArray(listing.property());
        ids.forEach(
            id -> {
              if (allows(clearance, markingsOf(listing.table(), id.asText()))) {
                visible.add(id);
              }
            });
      }
    }
    for (EmbeddedRule embedded : rule.embedded()) {
      RowRule elementRule = rules.forTable(embedded.table());
      arrayAt(filtered, embedded.path())
          .ifPresent(
              (container, property, elements) -> {
                ArrayNode visible = container.putArray(property);
                elements.forEach(
                    element -> {
                      if (rowVisible(elementRule, element, clearance)) {
                        visible.add(element);
                      }
                    });
              });
    }
    return filtered;
  }

  /** The ids, by table, whose markings the rules need: read once per event. */
  Map<String, Set<String>> referencedIds() {
    Map<String, Set<String>> ids = new HashMap<>();
    collect(rule.row(), payload, ids);
    for (ListingRule listing : rule.listings()) {
      payload
          .path(listing.property())
          .forEach(
              id -> ids.computeIfAbsent(listing.table(), t -> new HashSet<>()).add(id.asText()));
    }
    for (EmbeddedRule embedded : rule.embedded()) {
      RowRule elementRule = rules.forTable(embedded.table());
      arrayAt(payload, embedded.path())
          .ifPresent(
              (container, property, elements) ->
                  elements.forEach(element -> collect(elementRule, element, ids)));
    }
    return ids;
  }

  private static void collect(RowRule row, JsonNode node, Map<String, Set<String>> ids) {
    if (row == null || row.parentProperty() == null) {
      return;
    }
    JsonNode parent = node.path(row.parentProperty());
    Iterable<JsonNode> parentIds = parent.isArray() ? parent : java.util.List.of(parent);
    for (JsonNode id : parentIds) {
      if (id.isTextual()) {
        ids.computeIfAbsent(row.parentTable(), t -> new HashSet<>()).add(id.asText());
      }
    }
  }

  private boolean rowVisible(RowRule row, JsonNode node, MarkingCtx clearance) {
    if (row == null) {
      return true;
    }
    if (row.markingsProperty() != null) {
      return clearance.allows(textsOf(node.path(row.markingsProperty())));
    }
    if (row.parentProperty() == null) {
      return true;
    }
    JsonNode parent = node.path(row.parentProperty());
    if (row.about()) {
      // Like a finding in SQL: visible with no link, or with at least one visible link.
      return parent.isEmpty()
          || StreamSupport.stream(parent.spliterator(), false)
              .anyMatch(id -> allows(clearance, markingsOf(row.parentTable(), id.asText())));
    }
    // No parent (a global trace, a team expectation): like a NULL foreign key in SQL.
    return !parent.isTextual() || allows(clearance, markingsOf(row.parentTable(), parent.asText()));
  }

  private Set<String> markingsOf(String table, String id) {
    return markingsByTable.getOrDefault(table, Map.of()).getOrDefault(id, UNRESOLVED);
  }

  private static boolean allows(MarkingCtx clearance, Set<String> markings) {
    return clearance.allows(markings);
  }

  private static Set<String> textsOf(JsonNode array) {
    Set<String> texts = new HashSet<>();
    array.forEach(value -> texts.add(value.asText()));
    return texts;
  }

  /** The array at a dot-separated path, with the object holding it. */
  private static ArrayAt arrayAt(ObjectNode root, String path) {
    String[] steps = path.split("\\.");
    JsonNode container = root;
    for (int i = 0; i < steps.length - 1; i++) {
      container = container.path(steps[i]);
    }
    String property = steps[steps.length - 1];
    return container instanceof ObjectNode object && object.get(property) instanceof ArrayNode array
        ? new ArrayAt(object, property, array)
        : new ArrayAt(null, property, null);
  }

  private record ArrayAt(ObjectNode container, String property, ArrayNode elements) {

    void ifPresent(ArrayConsumer consumer) {
      if (container != null) {
        consumer.accept(container, property, elements);
      }
    }
  }

  @FunctionalInterface
  private interface ArrayConsumer {
    void accept(ObjectNode container, String property, ArrayNode elements);
  }
}

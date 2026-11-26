package io.openaev.service.stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.openaev.context.MarkingCtx;
import io.openaev.database.model.ExecutionStatus;
import io.openaev.database.model.ExecutionTraceAction;
import io.openaev.database.model.ExecutionTraceStatus;
import io.openaev.service.stream.StreamMarkingRules.EmbeddedRule;
import io.openaev.service.stream.StreamMarkingRules.EntityRule;
import io.openaev.service.stream.StreamMarkingRules.ListingRule;
import io.openaev.service.stream.StreamMarkingRules.RowRule;
import io.openaev.utils.InjectStatusUtils;
import jakarta.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

/**
 * What one event shows each reader, by clearance: built once per event, after commit, from its
 * payload and the markings of the rows it points to ({@link StreamVisibilityService}). A row whose
 * markings could not be read is visible only to a reader who sees everything: the stream fails
 * closed.
 */
public final class StreamVisibility {

  /** An event the marking rules do not apply to: sent to every reader as it is. */
  public static final StreamVisibility UNFILTERED =
      new StreamVisibility(null, null, null, null, null);

  /** Stands for markings that could not be read: no clearance but the unrestricted one holds it. */
  private static final Set<String> UNRESOLVED = Set.of("unresolved-marking");

  private final StreamMarkingRules rules;
  private final EntityRule rule;
  private final ObjectNode payload;
  private final Map<String, Map<String, Set<String>>> markingsByTable;

  /** The expected agent count of an inject's status: never serialized, so never sent. */
  private final Integer expectedAgentCount;

  StreamVisibility(
      StreamMarkingRules rules,
      EntityRule rule,
      ObjectNode payload,
      Map<String, Map<String, Set<String>>> markingsByTable,
      @Nullable Integer expectedAgentCount) {
    this.rules = rules;
    this.rule = rule;
    this.payload = payload;
    this.markingsByTable = markingsByTable;
    this.expectedAgentCount = expectedAgentCount;
  }

  /** An event whose visibility could not be computed: sent only to unrestricted readers. */
  public static StreamVisibility unresolved() {
    return new StreamVisibility(null, null, null, Map.of(), null);
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

  /**
   * Whether the row is hidden only because its parent row no longer exists: deleted with it, like
   * the agents of a deleted endpoint. The parent's markings were read, and the parent was not
   * found.
   */
  public boolean parentDeleted() {
    if (isUnfiltered() || rule == null || rule.row() == null) {
      return false;
    }
    RowRule row = rule.row();
    if (row.parentProperty() == null || row.about()) {
      return false;
    }
    JsonNode parent = payload.path(row.parentProperty());
    Map<String, Set<String>> read = markingsByTable.get(row.parentTable());
    return parent.isTextual() && read != null && !read.containsKey(parent.asText());
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
      keepOnly(
          filtered,
          listing.path(),
          id -> allows(clearance, markingsOf(listing.table(), id.asText())));
    }
    for (EmbeddedRule embedded : rule.embedded()) {
      RowRule elementRule = rules.forTable(embedded.table());
      keepOnly(filtered, embedded.path(), element -> rowVisible(elementRule, element, clearance));
    }
    showVisibleInjectStatus(filtered);
    return filtered;
  }

  /**
   * An inject's status depends on the traces its reader sees ({@link
   * InjectStatusUtils#visibleStatus}): recomputed once the traces are pruned, against the persisted
   * expected agent count, as the REST path does. Not the agents of the payload's COMPLETE traces:
   * deleting an agent deletes its traces, and a count read from them would drop with it, showing a
   * reader the stored verdict that includes the deleted agent's outcome. No-op on a payload that is
   * not an inject.
   */
  private void showVisibleInjectStatus(ObjectNode filtered) {
    if (!(filtered.get("inject_status") instanceof ObjectNode status)
        || !status.hasNonNull("status_name")) {
      return;
    }
    ExecutionStatus stored = ExecutionStatus.fromName(status.get("status_name").asText());
    status.put(
        "status_name",
        InjectStatusUtils.visibleStatus(stored, expectedAgentCount, completeTraces(filtered))
            .name());
  }

  private static List<InjectStatusUtils.CompleteTrace> completeTraces(JsonNode injectPayload) {
    List<InjectStatusUtils.CompleteTrace> traces = new ArrayList<>();
    injectPayload
        .path("inject_status")
        .path("status_traces")
        .forEach(
            trace -> {
              if (ExecutionTraceAction.COMPLETE
                      .name()
                      .equals(trace.path("execution_action").asText())
                  && trace.hasNonNull("execution_status")) {
                traces.add(
                    new InjectStatusUtils.CompleteTrace(
                        ExecutionTraceStatus.fromName(trace.get("execution_status").asText()),
                        trace.hasNonNull("agent") ? trace.get("agent").asText() : null));
              }
            });
    return traces;
  }

  /** The ids, by table, whose markings the rules need: read once per event. */
  Map<String, Set<String>> referencedIds() {
    Map<String, Set<String>> ids = new HashMap<>();
    collect(rule.row(), payload, ids);
    for (ListingRule listing : rule.listings()) {
      elementsAt(payload, listing.path())
          .forEach(
              id -> ids.computeIfAbsent(listing.table(), t -> new HashSet<>()).add(id.asText()));
    }
    for (EmbeddedRule embedded : rule.embedded()) {
      RowRule elementRule = rules.forTable(embedded.table());
      elementsAt(payload, embedded.path()).forEach(element -> collect(elementRule, element, ids));
    }
    return ids;
  }

  private static void collect(RowRule row, JsonNode node, Map<String, Set<String>> ids) {
    if (row == null || row.parentProperty() == null) {
      return;
    }
    JsonNode parent = node.path(row.parentProperty());
    Iterable<JsonNode> parentIds = parent.isArray() ? parent : List.of(parent);
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

  /**
   * The objects holding the last step of a dot-separated path, through nested objects and arrays:
   * {@code finding_asset_groups.asset_group_assets} reaches each group of the finding.
   */
  private static List<ObjectNode> holdersOf(JsonNode root, String path) {
    String[] steps = path.split("\\.");
    List<JsonNode> nodes = List.of(root);
    for (int i = 0; i < steps.length - 1; i++) {
      List<JsonNode> next = new ArrayList<>();
      for (JsonNode node : nodes) {
        JsonNode child = node.path(steps[i]);
        if (child.isArray()) {
          child.forEach(next::add);
        } else if (child.isObject()) {
          next.add(child);
        }
      }
      nodes = next;
    }
    return nodes.stream().filter(ObjectNode.class::isInstance).map(ObjectNode.class::cast).toList();
  }

  private static String lastStep(String path) {
    return path.substring(path.lastIndexOf('.') + 1);
  }

  private static List<JsonNode> elementsAt(JsonNode root, String path) {
    List<JsonNode> elements = new ArrayList<>();
    for (ObjectNode holder : holdersOf(root, path)) {
      JsonNode value = holder.path(lastStep(path));
      if (value.isArray()) {
        value.forEach(elements::add);
      } else if (value.isObject() || value.isTextual()) {
        elements.add(value);
      }
    }
    return elements;
  }

  /**
   * Keeps, in every array the path reaches, only the elements {@code visible} accepts; a single
   * object or id it does not accept becomes null.
   */
  private static void keepOnly(ObjectNode root, String path, Predicate<JsonNode> visible) {
    String property = lastStep(path);
    for (ObjectNode holder : holdersOf(root, path)) {
      JsonNode value = holder.get(property);
      if ((value instanceof ObjectNode || value instanceof TextNode) && !visible.test(value)) {
        holder.putNull(property);
      } else if (value instanceof ArrayNode elements) {
        ArrayNode kept = holder.putArray(property);
        elements.forEach(
            element -> {
              if (visible.test(element)) {
                kept.add(element);
              }
            });
      }
    }
  }
}

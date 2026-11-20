package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Three-way merge of the content of a catalogue scenario, field by field: the base its installed
 * version brought, the content applied, and the local one. A field only the client changed keeps
 * his value, one only the applied content changed takes its value, one both changed is a conflict
 * the client chooses.
 */
public final class ScenarioMerge {

  public enum Choice {
    KEEP_MINE,
    TAKE_APPLIED
  }

  public enum Status {
    TAKE_APPLIED,
    KEEP_MINE,
    CONFLICT,
    ADDED,
    REMOVED
  }

  /** A field, or a whole element when {@code field} is null, that the merge decides. */
  public record Change(
      String element,
      String field,
      String title,
      Status status,
      JsonNode base,
      JsonNode applied,
      JsonNode local) {

    public String id() {
      return field == null ? element : element + "/" + field;
    }
  }

  public record Result(List<Change> changes, ObjectNode merged, Set<String> unresolved) {

    public boolean resolved() {
      return unresolved.isEmpty();
    }
  }

  private final Map<String, Choice> choices;
  private final List<Change> changes = new ArrayList<>();
  private final Set<String> unresolved = new LinkedHashSet<>();

  private ScenarioMerge(Map<String, Choice> choices) {
    this.choices = choices;
  }

  public static Result merge(
      ObjectNode base, ObjectNode applied, ObjectNode local, Map<String, Choice> choices) {
    ScenarioMerge merge = new ScenarioMerge(choices);
    ObjectNode merged = JsonNodeFactory.instance.objectNode();
    for (String element : names(base, applied, local)) {
      merge
          .element(element, object(base, element), object(applied, element), object(local, element))
          .ifPresent(value -> merged.set(element, value));
    }
    return new Result(
        List.copyOf(merge.changes), merged, Collections.unmodifiableSet(merge.unresolved));
  }

  // An element the client deleted stays deleted; one the base never had is the client's own.
  private Optional<JsonNode> element(String element, ObjectNode b, ObjectNode a, ObjectNode l) {
    if (l == null) {
      if (b == null && a != null) {
        changes.add(new Change(element, null, title(a), Status.ADDED, null, a, null));
        return Optional.of(a);
      }
      return Optional.empty();
    }
    if (a == null) {
      if (b == null) {
        return Optional.of(l);
      }
      if (l.equals(b)) {
        changes.add(new Change(element, null, title(l), Status.REMOVED, b, null, l));
        return Optional.empty();
      }
      return choose(new Change(element, null, title(l), Status.CONFLICT, b, null, l));
    }
    return Optional.of(
        fields(element, b == null ? JsonNodeFactory.instance.objectNode() : b, a, l));
  }

  private ObjectNode fields(String element, ObjectNode b, ObjectNode a, ObjectNode l) {
    ObjectNode merged = JsonNodeFactory.instance.objectNode();
    String title = title(l);
    for (String field : names(b, a, l)) {
      value(element, field, title, b.get(field), a.get(field), l.get(field))
          .ifPresent(value -> merged.set(field, value));
    }
    return merged;
  }

  private Optional<JsonNode> value(
      String element, String field, String title, JsonNode b, JsonNode a, JsonNode l) {
    if (Objects.equals(a, l)) {
      return Optional.ofNullable(l);
    }
    if (Objects.equals(a, b)) {
      changes.add(new Change(element, field, title, Status.KEEP_MINE, b, a, l));
      return Optional.ofNullable(l);
    }
    if (Objects.equals(l, b)) {
      changes.add(new Change(element, field, title, Status.TAKE_APPLIED, b, a, l));
      return Optional.ofNullable(a);
    }
    return choose(new Change(element, field, title, Status.CONFLICT, b, a, l));
  }

  private Optional<JsonNode> choose(Change conflict) {
    changes.add(conflict);
    Choice choice = choices.get(conflict.id());
    if (choice == null) {
      unresolved.add(conflict.id());
    }
    return Optional.ofNullable(
        choice == Choice.TAKE_APPLIED ? conflict.applied() : conflict.local());
  }

  private static String title(ObjectNode element) {
    JsonNode title = element.get(ScenarioContentReader.TITLE);
    return title == null ? null : title.asText();
  }

  private static ObjectNode object(ObjectNode content, String name) {
    return content.get(name) instanceof ObjectNode element ? element : null;
  }

  private static Set<String> names(ObjectNode... nodes) {
    Set<String> names = new LinkedHashSet<>();
    for (ObjectNode node : nodes) {
      node.fieldNames().forEachRemaining(names::add);
    }
    return names;
  }
}

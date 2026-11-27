package io.openaev.service.catalog.scenario.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Three-way merge of the content of a catalogue scenario, field by field: the base its installed
 * version brought, the content applied, and the local one. A field only the client changed keeps
 * his value, one only the applied content changed takes its value, one both changed is a conflict
 * the client chooses.
 */
public final class ScenarioMerge {

  public enum Choice {
    KEEP_MINE {
      @Override
      JsonNode value(Change conflict, JsonNode edit) {
        return conflict.local();
      }
    },
    TAKE_APPLIED {
      @Override
      JsonNode value(Change conflict, JsonNode edit) {
        return conflict.applied();
      }
    },
    EDIT {
      @Override
      boolean fits(Change conflict, JsonNode edit) {
        return conflict.editable() && edit != null;
      }

      @Override
      JsonNode value(Change conflict, JsonNode edit) {
        return edit;
      }
    };

    boolean fits(Change conflict, JsonNode edit) {
      return true;
    }

    abstract JsonNode value(Change conflict, JsonNode edit);
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

    // Typing a value replaces a text, never a payload, a link or a severity the writer maps.
    private static final Set<String> FIXED_FIELDS =
        Set.of(
            ScenarioContentReader.CONTRACT,
            ScenarioContentReader.DEPENDS_DURATION,
            ScenarioContentReader.DEPENDS_ON,
            ScenarioContentReader.SEVERITY);

    public String id() {
      return field == null ? element : element + "/" + field;
    }

    public ScenarioElementKind elementKind() {
      return ScenarioContentReader.elementKind(element);
    }

    public ScenarioFieldKind fieldKind() {
      return field == null ? null : ScenarioContentReader.fieldKind(element, field);
    }

    public String fieldKey() {
      return field == null ? null : fieldKind().key(field);
    }

    public boolean editable() {
      return field != null
          && !FIXED_FIELDS.contains(field)
          && Stream.of(base, applied, local).allMatch(value -> value == null || value.isTextual());
    }
  }

  public record Result(List<Change> changes, ObjectNode merged, Set<String> unresolved) {

    public boolean resolved() {
      return unresolved.isEmpty();
    }

    /** The titles of the injects that lose a dependency on an inject the result no longer has. */
    public List<String> lostDependencies() {
      return merged.properties().stream()
          .filter(element -> element.getKey().startsWith(ScenarioContentReader.INJECT))
          .filter(
              element ->
                  StreamSupport.stream(
                          element.getValue().path(ScenarioContentReader.DEPENDS_ON).spliterator(),
                          false)
                      .anyMatch(
                          dependency ->
                              !merged.has(
                                  ScenarioContentReader.INJECT
                                      + dependency.path(ScenarioContentReader.PARENT).asText())))
          .map(element -> element.getValue().path(ScenarioContentReader.TITLE).asText())
          .toList();
    }
  }

  private final Map<String, Choice> choices;
  private final Map<String, String> edits;
  private final List<Change> changes = new ArrayList<>();
  private final Set<String> unresolved = new LinkedHashSet<>();

  private ScenarioMerge(Map<String, Choice> choices, Map<String, String> edits) {
    this.choices = choices;
    this.edits = edits;
  }

  public static Result merge(
      ObjectNode base, ObjectNode applied, ObjectNode local, Map<String, Choice> choices) {
    return merge(base, applied, local, choices, Map.of());
  }

  /** {@code edits} holds the value typed for each conflict chosen {@link Choice#EDIT}. */
  public static Result merge(
      ObjectNode base,
      ObjectNode applied,
      ObjectNode local,
      Map<String, Choice> choices,
      Map<String, String> edits) {
    ScenarioMerge merge = new ScenarioMerge(choices, edits);
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
    String typed = edits.get(conflict.id());
    JsonNode edit = typed == null ? null : TextNode.valueOf(typed);
    if (choice == null || !choice.fits(conflict, edit)) {
      unresolved.add(conflict.id());
      return Optional.ofNullable(conflict.local());
    }
    return Optional.ofNullable(choice.value(conflict, edit));
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

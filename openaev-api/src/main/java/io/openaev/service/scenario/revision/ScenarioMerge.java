package io.openaev.service.scenario.revision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;

/**
 * Three-way merge of a catalogue scenario (ADR-018, section 5): the base the catalogue last wrote,
 * the applied content (a release, or a revision for a rollback) and the local state, all in the
 * normalized format of {@link ScenarioContentReader}. Pure: no database, no entity.
 */
public final class ScenarioMerge {

  public static final String SCENARIO = "scenario";
  public static final String MATERIALS = "materials";
  public static final String VARIABLES = "variables";
  public static final String INJECTS = "injects";
  public static final String STEPS = "steps";
  public static final String CONTRACT = "contract";
  public static final String CONTENT = "content";
  public static final String TIMING = "timing";
  public static final String TEXT = "text";
  public static final String ARGUMENTS = "arguments";
  public static final String STEP = "step";
  public static final String DEPENDS_ON = "depends_on";
  public static final String PARENT = "parent";
  // A key the user added: never removed by an update or a rollback.
  public static final String LOCAL_PREFIX = "local:";

  static final List<String> INJECT_BLOCKS = List.of(CONTRACT, CONTENT, TIMING, TEXT);
  static final List<String> STEP_BLOCKS = List.of(STEP);

  public enum Choice {
    KEEP_MINE,
    TAKE_APPLIED,
    EDIT
  }

  // The payload version and the materials hashes are picked, never typed.
  private static final Set<String> FIXED_BLOCKS = Set.of(CONTRACT, MATERIALS);

  /** Whether a conflict on this block or value can take a value the client types. */
  public static boolean editable(String block, String key) {
    return key != null || (block != null && !FIXED_BLOCKS.contains(block));
  }

  public enum Status {
    TAKE_APPLIED,
    KEEP_MINE,
    SAME,
    CONFLICT
  }

  public enum ElementChange {
    ADDED,
    REMOVED,
    REMOVED_CONFLICT,
    STAYS_DELETED,
    KEPT_USER
  }

  /** A block or a value that differs somewhere; {@code id} is what a choice names. */
  public record Change(
      String id,
      String element,
      String title,
      String block,
      String key,
      Status status,
      JsonNode base,
      JsonNode applied,
      JsonNode local) {}

  public record Element(String id, String kind, String key, String title, ElementChange change) {}

  public record Result(
      List<Change> changes,
      List<Element> elements,
      List<String> warnings,
      ObjectNode merged,
      Set<String> unresolved) {

    public boolean resolved() {
      return unresolved.isEmpty();
    }
  }

  private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

  private final ObjectNode base;
  private final ObjectNode applied;
  private final ObjectNode local;
  private final Map<String, Choice> choices;
  private final Map<String, JsonNode> edits;
  private final List<Change> changes = new ArrayList<>();
  private final List<Element> elements = new ArrayList<>();
  private final List<String> warnings = new ArrayList<>();
  private final Set<String> unresolved = new LinkedHashSet<>();

  private ScenarioMerge(
      ObjectNode base,
      ObjectNode applied,
      ObjectNode local,
      Map<String, Choice> choices,
      Map<String, JsonNode> edits) {
    this.base = base;
    this.applied = applied;
    this.local = local;
    this.choices = choices == null ? Map.of() : choices;
    this.edits = edits == null ? Map.of() : edits;
  }

  public static Result merge(
      ObjectNode base, ObjectNode applied, ObjectNode local, Map<String, Choice> choices) {
    return merge(base, applied, local, choices, Map.of());
  }

  /** {@code edits} holds the value typed for each conflict whose choice is {@code EDIT}. */
  public static Result merge(
      ObjectNode base,
      ObjectNode applied,
      ObjectNode local,
      Map<String, Choice> choices,
      Map<String, JsonNode> edits) {
    return new ScenarioMerge(base, applied, local, choices, edits).run();
  }

  private Result run() {
    ObjectNode merged = JSON.objectNode();
    merged.set(
        SCENARIO,
        block(
            SCENARIO,
            SCENARIO,
            null,
            get(base, SCENARIO),
            get(applied, SCENARIO),
            get(local, SCENARIO)));
    merged.set(
        MATERIALS,
        block(
            SCENARIO,
            MATERIALS,
            null,
            get(base, MATERIALS),
            get(applied, MATERIALS),
            get(local, MATERIALS)));
    merged.set(VARIABLES, variables());
    merged.set(INJECTS, elements(INJECTS, INJECT_BLOCKS));
    merged.set(STEPS, elements(STEPS, STEP_BLOCKS));
    dropLostDependencies((ObjectNode) merged.get(INJECTS));
    return new Result(changes, elements, warnings, merged, unresolved);
  }

  // -- BLOCKS --

  private JsonNode block(
      String element, String block, String title, JsonNode b, JsonNode a, JsonNode l) {
    String id = element + ":" + block;
    if (Objects.equals(a, b)) {
      if (!Objects.equals(l, b)) {
        changes.add(new Change(id, element, title, block, null, Status.KEEP_MINE, b, a, l));
      }
      return l;
    }
    if (Objects.equals(l, b)) {
      changes.add(new Change(id, element, title, block, null, Status.TAKE_APPLIED, b, a, l));
      return a;
    }
    if (Objects.equals(a, l)) {
      changes.add(new Change(id, element, title, block, null, Status.SAME, b, a, l));
      return l;
    }
    changes.add(new Change(id, element, title, block, null, Status.CONFLICT, b, a, l));
    return resolve(id, a, l, editable(block, null));
  }

  private JsonNode resolve(String id, JsonNode applied, JsonNode mine) {
    return resolve(id, applied, mine, false);
  }

  // An edited block keeps the shape of the client's own; a bad or missing value stays unresolved.
  private JsonNode resolve(String id, JsonNode applied, JsonNode mine, boolean editable) {
    Choice choice = choices.get(id);
    if (choice == Choice.EDIT) {
      JsonNode edited = edits.get(id);
      boolean shapeKept = edited != null && (mine == null || !mine.isObject() || edited.isObject());
      if (editable && shapeKept) {
        return edited;
      }
      unresolved.add(id);
      return mine;
    }
    if (choice == null) {
      unresolved.add(id);
      return mine;
    }
    return choice == Choice.TAKE_APPLIED ? applied : mine;
  }

  // -- VALUES, KEY BY KEY --

  private JsonNode value(
      String element, String title, String block, String key, JsonNode b, JsonNode a, JsonNode l) {
    String id = element + ":" + block + ":" + key;
    if (l == null) {
      return a;
    }
    if (Objects.equals(l, b)) {
      if (!Objects.equals(a, b)) {
        changes.add(new Change(id, element, title, block, key, Status.TAKE_APPLIED, b, a, l));
      }
      return a;
    }
    if (Objects.equals(a, b)) {
      changes.add(new Change(id, element, title, block, key, Status.KEEP_MINE, b, a, l));
      return l;
    }
    if (Objects.equals(a, l)) {
      changes.add(new Change(id, element, title, block, key, Status.SAME, b, a, l));
      return l;
    }
    changes.add(new Change(id, element, title, block, key, Status.CONFLICT, b, a, l));
    return resolve(id, a, l, true);
  }

  private ObjectNode variables() {
    ObjectNode b = obj(get(base, VARIABLES));
    ObjectNode a = obj(get(applied, VARIABLES));
    ObjectNode l = obj(get(local, VARIABLES));
    ObjectNode merged = JSON.objectNode();
    for (String key : union(b, a, l)) {
      JsonNode bv = b.get(key);
      JsonNode av = a.get(key);
      JsonNode lv = l.get(key);
      if (lv == null) {
        // Removed by the client: it stays removed; new in the applied content: added.
        if (bv == null && av != null) {
          merged.set(key, av);
        }
        continue;
      }
      if (av == null) {
        // Removed by the applied content: dropped unless the client changed it.
        if (bv == null || !Objects.equals(lv, bv)) {
          merged.set(key, lv);
        }
        continue;
      }
      merged.set(
          key, value(VARIABLES, key, VARIABLES, key, bv == null ? NullNode.instance : bv, av, lv));
    }
    return merged;
  }

  // -- INJECTS AND STEPS --

  private ObjectNode elements(String kind, List<String> blocks) {
    ObjectNode b = obj(get(base, kind));
    ObjectNode a = obj(get(applied, kind));
    ObjectNode l = obj(get(local, kind));
    ObjectNode merged = JSON.objectNode();
    for (String key : union(b, a, l)) {
      ObjectNode be = (ObjectNode) b.get(key);
      ObjectNode ae = (ObjectNode) a.get(key);
      ObjectNode le = (ObjectNode) l.get(key);
      String element = kind + ":" + key;
      if (le == null) {
        if (be != null && ae != null) {
          elements.add(new Element(element, kind, key, title(ae), ElementChange.STAYS_DELETED));
        } else if (be == null && ae != null) {
          elements.add(new Element(element, kind, key, title(ae), ElementChange.ADDED));
          merged.set(key, ae);
        }
        continue;
      }
      if (ae == null) {
        if (key.startsWith(LOCAL_PREFIX) || be == null) {
          if (!key.startsWith(LOCAL_PREFIX)) {
            elements.add(new Element(element, kind, key, title(le), ElementChange.KEPT_USER));
          }
          merged.set(key, le);
        } else if (Objects.equals(le, be)) {
          elements.add(new Element(element, kind, key, title(le), ElementChange.REMOVED));
        } else {
          elements.add(new Element(element, kind, key, title(le), ElementChange.REMOVED_CONFLICT));
          // Kept, it becomes a user element: its catalogue ID is dropped.
          if (resolve(element, NullNode.instance, le) != NullNode.instance) {
            merged.set(LOCAL_PREFIX + key, le);
          }
        }
        continue;
      }
      merged.set(key, mergeElement(element, blocks, be == null ? JSON.objectNode() : be, ae, le));
    }
    return merged;
  }

  private ObjectNode mergeElement(
      String element, List<String> blocks, ObjectNode b, ObjectNode a, ObjectNode l) {
    ObjectNode merged = JSON.objectNode();
    for (String block : blocks) {
      merged.set(block, block(element, block, title(l), b.get(block), a.get(block), l.get(block)));
    }
    if (blocks.contains(CONTRACT)) {
      merged.set(ARGUMENTS, arguments(element, title(l), merged.get(CONTRACT), b, a, l));
    }
    return merged;
  }

  /** The keys are those of the contract kept: the client's own arguments if he kept his. */
  private ObjectNode arguments(
      String element, String title, JsonNode contract, ObjectNode b, ObjectNode a, ObjectNode l) {
    ObjectNode la = obj(l.get(ARGUMENTS));
    if (!Objects.equals(contract, a.get(CONTRACT))) {
      warnings.add("OLDER_PAYLOAD_VERSION:" + element);
      return la;
    }
    ObjectNode ba = obj(b.get(ARGUMENTS));
    ObjectNode aa = obj(a.get(ARGUMENTS));
    ObjectNode merged = JSON.objectNode();
    aa.fieldNames()
        .forEachRemaining(
            key -> {
              JsonNode value =
                  value(element, title, ARGUMENTS, key, ba.get(key), aa.get(key), la.get(key));
              if (value == null || value.isNull()) {
                warnings.add("MISSING_VALUE:" + element + ":" + key);
              }
              merged.set(key, value);
            });
    la.fieldNames()
        .forEachRemaining(
            key -> {
              if (!aa.has(key) && !Objects.equals(la.get(key), ba.get(key))) {
                warnings.add("DROPPED_VALUE:" + element + ":" + key);
              }
            });
    return merged;
  }

  private void dropLostDependencies(ObjectNode injects) {
    injects
        .properties()
        .forEach(
            entry -> {
              JsonNode timing = entry.getValue().get(TIMING);
              if (timing == null || !(timing.get(DEPENDS_ON) instanceof ArrayNode dependsOn)) {
                return;
              }
              ArrayNode kept = JSON.arrayNode();
              dependsOn.forEach(
                  dependency -> {
                    String parent = dependency.path(PARENT).asText();
                    if (injects.has(parent)) {
                      kept.add(dependency);
                    } else {
                      warnings.add("LOST_DEPENDENCY:" + INJECTS + ":" + entry.getKey());
                    }
                  });
              if (kept.size() != dependsOn.size()) {
                ObjectNode copy = entry.getValue().deepCopy();
                ((ObjectNode) copy.get(TIMING)).set(DEPENDS_ON, kept);
                injects.set(entry.getKey(), copy);
              }
            });
  }

  // -- HELPERS --

  private static String title(JsonNode element) {
    JsonNode text = element == null ? null : element.get(TEXT);
    if (text != null && text.hasNonNull("title")) {
      return text.get("title").asText();
    }
    JsonNode step = element == null ? null : element.get(STEP);
    return step != null && step.hasNonNull("name") ? step.get("name").asText() : null;
  }

  private static JsonNode get(JsonNode node, String field) {
    return node == null ? null : node.get(field);
  }

  private static ObjectNode obj(JsonNode node) {
    return node instanceof ObjectNode o ? o : JSON.objectNode();
  }

  private static Set<String> union(ObjectNode... nodes) {
    Set<String> keys = new LinkedHashSet<>();
    for (ObjectNode node : nodes) {
      node.fieldNames().forEachRemaining(keys::add);
    }
    return keys;
  }
}

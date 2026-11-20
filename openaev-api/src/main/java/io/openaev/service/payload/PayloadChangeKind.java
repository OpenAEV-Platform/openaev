package io.openaev.service.payload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Payload;
import io.openaev.database.model.hash.Hashed;
import io.openaev.service.hash.CanonicalContent;
import io.openaev.service.hash.UnreadableFileException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** What a new content of a payload changes, from the fields of its hashed content. */
@Service
@RequiredArgsConstructor
public class PayloadChangeKind {

  private final PayloadContentHashService payloadContentHashService;

  /**
   * The keys whose value changed, each with what it makes of a new version, as the catalogue's
   * rule: anything removed from a list is major.
   */
  public PayloadChange of(Payload before, Payload after, Collection<AttackPattern> afterPatterns) {
    return compare(
        before,
        after,
        files -> payloadContentHashService.canonicalTree(after, afterPatterns, files));
  }

  /** Between two saved versions, each with the techniques of its own contract. */
  public PayloadChange between(Payload before, Payload after) {
    return compare(before, after, files -> payloadContentHashService.canonicalTree(after, files));
  }

  private PayloadChange compare(
      Payload before, Payload after, Function<CanonicalContent.Files, ObjectNode> afterTree) {
    Comparison comparison;
    try {
      comparison = compare(before, afterTree, CanonicalContent.Files.BY_HASH);
    } catch (UnreadableFileException e) {
      comparison = compare(before, afterTree, CanonicalContent.Files.BY_ID);
    }
    return comparison.change(payloadContentHashService.changes(after));
  }

  private Comparison compare(
      Payload before,
      Function<CanonicalContent.Files, ObjectNode> afterTree,
      CanonicalContent.Files files) {
    return new Comparison(
        payloadContentHashService.canonicalTree(before, files), afterTree.apply(files));
  }

  private record Comparison(ObjectNode before, ObjectNode after) {

    PayloadChange change(Map<String, Hashed.Change> changes) {
      Set<String> keys = new HashSet<>();
      before.fieldNames().forEachRemaining(keys::add);
      after.fieldNames().forEachRemaining(keys::add);
      Map<String, Hashed.Change> changed = new HashMap<>();
      keys.stream()
          .filter(key -> !Objects.equals(before.get(key), after.get(key)))
          .forEach(
              key ->
                  changed.put(
                      key,
                      removes(before.get(key), after.get(key))
                          ? Hashed.Change.MAJOR
                          : changes.getOrDefault(key, Hashed.Change.MAJOR)));
      return new PayloadChange(before, after, changed);
    }

    // An item of a list or a map that the new value no longer has. An object edited in a list is
    // a change, not a removal: a list of objects removes one when it gets shorter.
    private static boolean removes(JsonNode before, JsonNode after) {
      if (before == null || !before.isContainerNode()) {
        return false;
      }
      if (after == null || after.getNodeType() != before.getNodeType()) {
        return true;
      }
      if (before.isArray()) {
        if (StreamSupport.stream(before.spliterator(), false).anyMatch(JsonNode::isObject)) {
          return after.size() < before.size();
        }
        Set<JsonNode> kept = new HashSet<>();
        after.forEach(kept::add);
        return StreamSupport.stream(before.spliterator(), false)
            .anyMatch(item -> !kept.contains(item));
      }
      Set<String> fields = new HashSet<>();
      before.fieldNames().forEachRemaining(fields::add);
      return fields.stream()
          .anyMatch(field -> removes(before.get(field), after.get(field)) || !after.has(field));
    }
  }
}

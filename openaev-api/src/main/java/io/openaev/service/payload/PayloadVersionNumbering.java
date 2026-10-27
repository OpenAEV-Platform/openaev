package io.openaev.service.payload;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Payload;
import io.openaev.utils.SemanticVersion;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Version numbers of local payloads and variants (RFC 0005 §4.1), from what changed in the hashed
 * content: a version is only created when the hash changes.
 */
@Service
@RequiredArgsConstructor
public class PayloadVersionNumbering {

  public enum Change {
    MAJOR,
    MINOR,
    PATCH
  }

  // What an inject must adapt to: its argument values, where it runs, how it runs.
  private static final Set<String> MAJOR_FIELDS =
      Set.of(
          "payload_arguments", "payload_platforms", "command_executor", "payload_cleanup_executor");
  private static final Set<String> TEXT_FIELDS = Set.of("payload_name", "payload_description");

  private final PayloadContentHashService payloadContentHashService;

  /** The kind of change between two contents; empty when the hashed content is the same. */
  public Optional<Change> change(
      Payload before,
      Collection<AttackPattern> beforePatterns,
      Payload after,
      Collection<AttackPattern> afterPatterns) {
    ObjectNode left;
    ObjectNode right;
    try {
      left = payloadContentHashService.canonicalTree(before, beforePatterns);
      right = payloadContentHashService.canonicalTree(after, afterPatterns);
    } catch (RuntimeException e) {
      // An attached file cannot be read: the content cannot be compared, keep both versions.
      return Optional.of(Change.MINOR);
    }
    Set<String> fields = new HashSet<>();
    left.fieldNames().forEachRemaining(fields::add);
    right.fieldNames().forEachRemaining(fields::add);
    Set<String> changed = new HashSet<>();
    for (String field : fields) {
      if (!Objects.equals(left.get(field), right.get(field))) {
        changed.add(field);
      }
    }
    if (changed.isEmpty()) {
      return Optional.empty();
    }
    if (changed.stream().anyMatch(MAJOR_FIELDS::contains)) {
      return Optional.of(Change.MAJOR);
    }
    return Optional.of(TEXT_FIELDS.containsAll(changed) ? Change.PATCH : Change.MINOR);
  }

  public static String bump(String version, Change change) {
    SemanticVersion current = SemanticVersion.parse(version);
    if (current.major() < 0) {
      current = SemanticVersion.parse(Payload.FIRST_VERSION);
    }
    return switch (change) {
      case MAJOR -> (current.major() + 1) + ".0.0";
      case MINOR -> current.major() + "." + (current.minor() + 1) + ".0";
      case PATCH -> current.major() + "." + current.minor() + "." + (current.patch() + 1);
    };
  }
}

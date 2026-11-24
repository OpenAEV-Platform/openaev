package io.openaev.service.payload;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.hash.Hashed;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Two contents of a payload in their canonical form, and what each changed key makes of them. */
public record PayloadChange(
    ObjectNode before, ObjectNode after, Map<String, Hashed.Change> changes) {

  public Set<String> keys() {
    return changes.keySet();
  }

  /** Empty when nothing hashed changed; else the strongest change, declared strongest first. */
  public Optional<Hashed.Change> kind() {
    return changes.values().stream().min(Comparator.naturalOrder());
  }
}

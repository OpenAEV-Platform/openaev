package io.openaev.finding.identity;

import io.openaev.database.model.ContractOutputType;
import java.util.Objects;
import java.util.Set;
import lombok.Builder;

/**
 * One finding detected by an inject, whatever its source (injector, agent, phishing): the single
 * input of the finding writer.
 *
 * @param type the contract output type
 * @param value the cleartext value, as built by the output processor
 * @param field the contract output key, stored on the occurrence and not part of the identity
 * @param name the display name of the finding
 * @param location where it was detected
 * @param tagIds the tags to put on the finding
 * @param valueHash computed from the type, the value and the location: the one passed is ignored
 */
@Builder(toBuilder = true)
public record DetectedFinding(
    ContractOutputType type,
    String value,
    String field,
    String name,
    LocationIdentity location,
    Set<String> tagIds,
    String valueHash) {

  public DetectedFinding {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    if (value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
    Objects.requireNonNull(location, "location");
    tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
    valueHash = FindingValueNormalizer.hash(type, value, location);
  }

  public DetectedFinding(
      ContractOutputType type,
      String value,
      String field,
      String name,
      LocationIdentity location,
      Set<String> tagIds) {
    this(type, value, field, name, location, tagIds, null);
  }

  @Override
  public String toString() {
    return "DetectedFinding[type="
        + type
        + ", field="
        + field
        + ", name="
        + name
        + ", location="
        + location
        + ", tagIds="
        + tagIds
        + "]";
  }
}

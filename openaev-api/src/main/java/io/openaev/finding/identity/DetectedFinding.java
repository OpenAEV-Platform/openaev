package io.openaev.finding.identity;

import io.openaev.database.model.ContractOutputType;
import java.util.Objects;
import java.util.Set;

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
 */
public record DetectedFinding(
    ContractOutputType type,
    String value,
    String field,
    String name,
    LocationIdentity location,
    Set<String> tagIds) {

  public DetectedFinding {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(location, "location");
    tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
  }

  public String valueHash() {
    return FindingValueNormalizer.hash(type, value, location);
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

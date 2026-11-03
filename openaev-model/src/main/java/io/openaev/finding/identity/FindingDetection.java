package io.openaev.finding.identity;

import io.openaev.database.model.ContractOutputType;
import java.util.Objects;
import java.util.Set;

/**
 * One finding detected by an inject, whatever its source (injector, agent, phishing, manual): the
 * single input of the finding writer.
 *
 * @param type the contract output type
 * @param value the cleartext value, as built by the output processor
 * @param name the display name of the finding
 * @param location where it was detected
 * @param tagIds the tags to put on the finding
 */
public record FindingDetection(
    ContractOutputType type,
    String value,
    String name,
    FindingLocation location,
    Set<String> tagIds) {

  public FindingDetection {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(value, "value");
    Objects.requireNonNull(location, "location");
    tagIds = tagIds == null ? Set.of() : Set.copyOf(tagIds);
  }

  /** The identity this detection resolves to: same identity, same finding. */
  public FindingIdentity identity() {
    return FindingIdentity.of(type, value, location);
  }

  @Override
  public String toString() {
    return "FindingDetection[type="
        + type
        + ", name="
        + name
        + ", location="
        + location.key()
        + ", tagIds="
        + tagIds
        + "]";
  }
}

package io.openaev.service.payload;

import com.fasterxml.jackson.databind.JsonNode;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Payload;
import io.openaev.service.hash.CanonicalContent;
import io.openaev.utils.catalog.SemanticVersion;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

/** Which installed version of a payload an imported one stands for. */
@Service
@RequiredArgsConstructor
public class PayloadVersionMatcher {

  private final PayloadContentHashService payloadContentHashService;
  private final CanonicalContent canonicalContent;

  /**
   * The version with the same content. When none, an export holding every hashed field is a content
   * of its own: none, it is kept as a local copy; an older export, whose content is unknown, stands
   * for the highest version.
   */
  public Optional<Payload> match(
      List<Payload> versions,
      Payload imported,
      Collection<AttackPattern> attackPatterns,
      JsonNode exported) {
    Optional<Payload> identical =
        payloadContentHashService
            .hash(imported, attackPatterns)
            .flatMap(
                hash ->
                    versions.stream()
                        .filter(version -> hash.equalsIgnoreCase(version.getContentHash()))
                        .findFirst());
    if (identical.isPresent() || hasEveryHashedField(imported, exported)) {
      return identical;
    }
    return highest(versions);
  }

  public Optional<Payload> highest(List<Payload> versions) {
    return versions.stream()
        .max(
            Comparator.comparing(
                Payload::getVersion, Comparator.nullsFirst(SemanticVersion.ORDER)));
  }

  private boolean hasEveryHashedField(Payload imported, JsonNode exported) {
    return canonicalContent.keys(Hibernate.getClass(imported)).stream().allMatch(exported::has);
  }
}

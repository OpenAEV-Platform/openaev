package io.openaev.service.payload_approval;

import io.openaev.database.model.Payload;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Which payload sources are trusted: a version they deliver is approved on arrival, without an
 * approver. Only catalogue versions (managed, read-only, checked against their signed release) can
 * be trusted. Today Filigran is the only trusted source; a source left out of the list delivers
 * versions that wait for approval like any other.
 */
@Component
public class PayloadTrustPolicy {

  private final Set<Payload.PAYLOAD_SOURCE> trustedSources;

  public PayloadTrustPolicy(
      @Value("${openaev.payload-approval.trusted-sources:FILIGRAN}") final List<String> sources) {
    Set<Payload.PAYLOAD_SOURCE> trusted = EnumSet.noneOf(Payload.PAYLOAD_SOURCE.class);
    sources.stream()
        .map(String::trim)
        .filter(source -> !source.isEmpty())
        .map(Payload.PAYLOAD_SOURCE::valueOf)
        .forEach(trusted::add);
    this.trustedSources = trusted;
  }

  /** Whether this version comes from a trusted source, hence is approved on arrival. */
  public boolean isTrusted(@NotNull final Payload version) {
    return version.isManaged() && trustedSources.contains(version.getSource());
  }
}

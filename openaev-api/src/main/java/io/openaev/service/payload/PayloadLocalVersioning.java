package io.openaev.service.payload;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Payload;
import io.openaev.database.model.hash.Hashed;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.repository.PayloadIdentityRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.DetectionRemediationUtils;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.utils.catalog.SemanticVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Catalogue preview on: a content change of a local payload adds a version, injects keep theirs.
 */
@Service
@RequiredArgsConstructor
public class PayloadLocalVersioning {

  private final PayloadService payloadService;
  private final PayloadUtils payloadUtils;
  private final DetectionRemediationUtils detectionRemediationUtils;
  private final PayloadChangeKind payloadChangeKind;
  private final PayloadVersionMatcher payloadVersionMatcher;
  private final PayloadIdentityRepository payloadIdentityRepository;
  private final PreviewFeatureService previewFeatureService;

  /** The new version this input makes; empty when the payload is to be updated in place. */
  public Optional<Payload> newVersion(
      Payload current, PayloadUpdateInput input, List<AttackPattern> attackPatterns) {
    // A collector finds its payload by external id: it must stay one version.
    if (current.isManaged()
        || current.isFromCollector()
        || !previewFeatureService.isFeatureEnabled(PreviewFeature.CATALOG)) {
      return Optional.empty();
    }
    Payload candidate = candidate(current, input);
    return payloadChangeKind
        .of(current, candidate, attackPatterns)
        .map(change -> linkAsNewest(current, candidate, change));
  }

  // A duplicate carries the grants of the version; the input then replaces its content.
  private Payload candidate(Payload current, PayloadUpdateInput input) {
    Payload candidate = payloadService.generateDuplicatedPayload(current);
    payloadUtils.copyProperties(input.withoutChildIds(), candidate);
    if (input.getDetectionRemediations() == null) {
      // Without the license the rules are not sent: they stay as they are.
      detectionRemediationUtils.copy(current.getDetectionRemediations(), candidate, false);
    }
    return candidate;
  }

  // Locked: two saves at once number one after the other instead of taking the same number.
  private Payload linkAsNewest(Payload current, Payload candidate, Hashed.Change change) {
    PayloadIdentity identity =
        payloadIdentityRepository
            .findForUpdateById(current.getIdentity().getId())
            .orElseThrow(ElementNotFoundException::new);
    Payload newest =
        payloadVersionMatcher
            .highest(payloadService.versionsOf(current.getTenant().getId(), current))
            .orElse(current);
    candidate.setIdentity(identity);
    candidate.setVersion(next(SemanticVersion.parse(newest.getVersion()), change));
    // The duplicate resets the status and keeps the original's creation date.
    candidate.setStatus(current.getStatus());
    candidate.setCreatedAt(Instant.now());
    candidate.setUpdatedAt(candidate.getCreatedAt());
    identity.setLatestVersion(candidate);
    return candidate;
  }

  private static String next(SemanticVersion newest, Hashed.Change change) {
    return switch (change) {
      case MAJOR -> newest.nextMajor();
      case MINOR -> newest.nextMinor();
      case PATCH -> newest.nextPatch();
    };
  }
}

package io.openaev.service.payload;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Payload;
import io.openaev.database.model.hash.Hashed;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.repository.PayloadIdentityRepository;
import io.openaev.rest.exception.BadRequestException;
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

  public static final String LOCKED_ON_CUSTOMIZED_COPY =
      "A customized copy only changes its commands, arguments, prerequisites and platforms";

  private final PayloadService payloadService;
  private final PayloadUtils payloadUtils;
  private final DetectionRemediationUtils detectionRemediationUtils;
  private final PayloadChangeKind payloadChangeKind;
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
    PayloadChange change = payloadChangeKind.of(current, candidate, attackPatterns);
    if (current.isCustomized()) {
      refuseLockedChanges(change);
    }
    return change.kind().map(kind -> addVersion(current, candidate, kind));
  }

  // A customized copy takes everything else from its Filigran version.
  private static void refuseLockedChanges(PayloadChange change) {
    if (change.keys().stream().anyMatch(key -> CustomizableField.ofKey(key).isEmpty())) {
      throw new BadRequestException(LOCKED_ON_CUSTOMIZED_COPY);
    }
  }

  // A duplicate carries the grants of the version; the input then replaces its content.
  private Payload candidate(Payload current, PayloadUpdateInput input) {
    Payload candidate = payloadService.generateDuplicatedPayload(current);
    payloadUtils.copyProperties(input.withoutChildIds(), candidate);
    candidate.setBase(current.getBase());
    if (input.getDetectionRemediations() == null) {
      // Without the license the rules are not sent: they stay as they are.
      detectionRemediationUtils.copy(current.getDetectionRemediations(), candidate, false);
    }
    return candidate;
  }

  /**
   * Numbers the candidate after the newest version and makes it the one shown. Locked: two saves at
   * once number one after the other instead of taking the same number.
   */
  public Payload addVersion(Payload current, Payload candidate, Hashed.Change change) {
    PayloadIdentity identity =
        payloadIdentityRepository
            .findForUpdateById(current.getIdentity().getId())
            .orElseThrow(ElementNotFoundException::new);
    Payload newest =
        SemanticVersion.highest(
                payloadService.versionsOf(current.getTenant().getId(), current),
                Payload::getVersion)
            .orElse(current);
    candidate.setIdentity(identity);
    candidate.setVersion(change.next(SemanticVersion.parse(newest.getVersion())));
    // The duplicate resets the status and keeps the original's creation date.
    candidate.setStatus(current.getStatus());
    candidate.setCreatedAt(Instant.now());
    candidate.setUpdatedAt(candidate.getCreatedAt());
    identity.setLatestVersion(candidate);
    return candidate;
  }
}

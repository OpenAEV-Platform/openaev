package io.openaev.rest.payload.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.DomainRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.service.UserService;
import io.openaev.service.payload_approval.PayloadApprovalService;
import io.openaev.service.payload_approval.PayloadFingerprint;
import io.openaev.service.payload_approval.PayloadUsage;
import io.openaev.service.payload_approval.PayloadUsageService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PayloadUpdateService {

  private final PayloadUtils payloadUtils;

  private final PayloadService payloadService;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;

  private final TagRepository tagRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final DomainRepository domainRepository;
  private final PayloadRepository payloadRepository;
  private final DocumentService documentService;
  private final UserService userService;
  private final PayloadApprovalService payloadApprovalService;
  private final PayloadUsageService payloadUsageService;

  @Transactional(rollbackFor = Exception.class)
  public PayloadCreationService.PayloadInjectorContractCreationResult updatePayload(
      String payloadId, PayloadUpdateInput input) {
    return doUpdatePayload(payloadId, input, false);
  }

  /**
   * Same as {@link #updatePayload(String, PayloadUpdateInput)}; with {@code checkApprovalImpact},
   * refuses (nothing written) an edit that would send an approved payload in use back to pending,
   * so the UI can warn first. See {@link PayloadUsageService}.
   */
  @Transactional(rollbackFor = Exception.class)
  public PayloadCreationService.PayloadInjectorContractCreationResult updatePayload(
      String payloadId, PayloadUpdateInput input, boolean checkApprovalImpact) {
    return doUpdatePayload(payloadId, input, checkApprovalImpact);
  }

  // Non-transactional body shared by both @Transactional entry points: an intra-class call to a
  // @Transactional method bypasses the Spring proxy (self-invocation), so the overloads never call
  // each other directly.
  private PayloadCreationService.PayloadInjectorContractCreationResult doUpdatePayload(
      String payloadId, PayloadUpdateInput input, boolean checkApprovalImpact) {
    if (enterpriseEditionService.isEnterpriseLicenseInactive(
        licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }

    Payload payload =
        this.payloadRepository.findById(payloadId).orElseThrow(ElementNotFoundException::new);
    // Same guard as PayloadCreationService: callers converting action inputs (threat arsenal
    // update) can carry null id collections, which findAllById rejects with "Ids must not be
    // null". An absent collection means "no associations".
    List<AttackPattern> attackPatterns =
        fromIterable(
            attackPatternRepository.findAllById(emptyIfNull(input.getAttackPatternsIds())));
    return update(input, payload, attackPatterns, checkApprovalImpact);
  }

  private PayloadCreationService.PayloadInjectorContractCreationResult update(
      PayloadUpdateInput input,
      Payload existingPayload,
      List<AttackPattern> attackPatterns,
      boolean checkApprovalImpact) {
    PayloadType payloadType = PayloadType.fromString(existingPayload.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = (Payload) Hibernate.unproxy(existingPayload);
    String fingerprintBefore = PayloadFingerprint.of(payload);
    // Null outside an authenticated request (system flows): an unknown modifier, never a stale one.
    User actor = userService.currentUserOrNull();
    // Read before the edit is applied, so the usage queries never flush a half-edited payload.
    PayloadUsage usageAtRisk =
        checkApprovalImpact ? payloadUsageService.usageAtRiskOfEdit(payload, actor) : null;
    payloadUtils.copyProperties(input, payload);
    payload.setLastModifiedBy(actor);

    // Somehow, loading tags can create a detached error on detection remediation.
    // Detaching the collection before and reattaching it after bypass the issue
    List<DetectionRemediation> originalDrs = new ArrayList<>(payload.getDetectionRemediations());
    payload.setDetectionRemediations(Collections.emptyList());
    payload.setDetectionRemediations(originalDrs);

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }

    payloadUsageService.refuseUnconfirmedSendBackToPending(usageAtRisk, fingerprintBefore, payload);
    Payload saved = payloadRepository.save(payload);
    payloadApprovalService.onWrite(
        saved, saved.getLastModifiedBy(), PayloadApproval.ORIGIN.UPDATE, fingerprintBefore);
    InjectorContract injectorContract =
        payloadService.synchroniseInjectorContractBasedOnPayload(
            saved,
            attackPatterns,
            iterableToSet(domainRepository.findAllById(emptyIfNull(input.getDomainIds()))),
            iterableToSet(tagRepository.findAllById(emptyIfNull(input.getTagIds()))));
    return new PayloadCreationService.PayloadInjectorContractCreationResult(
        saved, injectorContract);
  }
}

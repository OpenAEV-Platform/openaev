package io.openaev.rest.payload.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.DomainRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exception.ManagedPayloadReadOnlyException;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import io.openaev.service.GrantService;
import io.openaev.service.UserService;
import io.openaev.service.content.ContentHistoryService;
import io.openaev.service.payload.PayloadFieldChangeService;
import io.openaev.service.payload.PayloadVersionNumbering;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.service.payload_approval.PayloadApprovalService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a local payload (ADR-018): a change of its hashed content adds a new version, the previous
 * row never changes; tags and domains are metadata, saved on the version as it is.
 */
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
  private final InjectorContractRepository injectorContractRepository;
  private final DocumentService documentService;
  private final ContentHistoryService contentHistoryService;
  private final PayloadVersionService payloadVersionService;
  private final PayloadVersionNumbering payloadVersionNumbering;
  private final GrantService grantService;

  /**
   * @param previous the version saved over, when a new one was created; null otherwise
   * @param previousUsages injects and chained steps still on the previous version
   */
  public record PayloadSaveResult(
      Payload payload, InjectorContract injectorContract, Payload previous, int previousUsages) {

    public boolean newVersion() {
      return previous != null;
    }

    public PayloadCreationService.PayloadInjectorContractCreationResult creation() {
      return new PayloadCreationService.PayloadInjectorContractCreationResult(
          payload, injectorContract);
    }
  }

  private final UserService userService;
  private final PayloadApprovalService payloadApprovalService;
  private final PayloadFieldChangeService payloadFieldChangeService;

  @Transactional(rollbackFor = Exception.class)
  public PayloadSaveResult updatePayload(String payloadId, PayloadUpdateInput input) {
    return updatePayload(payloadId, input, false);
  }

  /**
   * Saves a version of a payload as its next version.
   *
   * @param fromDraft the content comes from a draft started from this version: the history says so
   */
  public PayloadSaveResult updatePayload(
      String payloadId, PayloadUpdateInput input, boolean fromDraft) {
    if (enterpriseEditionService.isEnterpriseLicenseInactive(
        licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }

    Payload payload =
        this.payloadRepository.findById(payloadId).orElseThrow(ElementNotFoundException::new);
    if (PayloadVersionService.isManaged(payload)) {
      throw new ManagedPayloadReadOnlyException(
          "Payload " + payloadId + " is a managed version and is read-only: duplicate it to edit.");
    }
    if (payload.getIdentity().isVariant()) {
      throw new BadRequestException(
          "Payload " + payloadId + " is customized: change its overrides instead.");
    }
    // Same guard as PayloadCreationService: callers converting action inputs (threat arsenal
    // update) can carry null id collections, which findAllById rejects with "Ids must not be
    // null". An absent collection means "no associations".
    List<AttackPattern> attackPatterns =
        fromIterable(
            attackPatternRepository.findAllById(emptyIfNull(input.getAttackPatternsIds())));
    return save(input, (Payload) Hibernate.unproxy(payload), attackPatterns, fromDraft);
  }

  /**
   * Payload drafts: what saving this content would change in the version, field by field, without
   * saving anything. The caller runs it in a read-only transaction: the candidate is never stored.
   */
  public ArrayNode contentChanges(String payloadId, PayloadUpdateInput input) {
    if (enterpriseEditionService.isEnterpriseLicenseInactive(
        licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }
    Payload current =
        (Payload)
            Hibernate.unproxy(
                this.payloadRepository
                    .findById(payloadId)
                    .orElseThrow(ElementNotFoundException::new));
    Payload candidate = payloadService.copyForNewVersion(current);
    payloadUtils.copyProperties(input, candidate);
    if (candidate instanceof Executable executable) {
      executable.setExecutableFile(documentOrNull(input.getExecutableFile()));
    } else if (candidate instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentOrNull(input.getFileDropFile()));
    }
    List<AttackPattern> attackPatterns =
        fromIterable(
            attackPatternRepository.findAllById(emptyIfNull(input.getAttackPatternsIds())));
    return payloadFieldChangeService.between(
        current, payloadFieldChangeService.attackPatternsOf(current), candidate, attackPatterns);
  }

  private Document documentOrNull(String documentId) {
    return documentId == null ? null : documentService.document(documentId);
  }

  /**
   * Payload drafts: rewrites a rejected version with the content fixed in its draft, keeping its
   * number, and sends it for approval again. Only a rejected version can be rewritten: it never
   * ran, and nothing can run it until it is approved.
   */
  public PayloadSaveResult rework(String payloadId, PayloadUpdateInput input) {
    if (enterpriseEditionService.isEnterpriseLicenseInactive(
        licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }
    Payload current =
        (Payload)
            Hibernate.unproxy(
                this.payloadRepository
                    .findById(payloadId)
                    .orElseThrow(ElementNotFoundException::new));
    if (PayloadVersionService.isManaged(current) || current.getIdentity().isVariant()) {
      throw new BadRequestException("Payload " + payloadId + " cannot be changed in place.");
    }
    if (current.getApprovalStatus() != Payload.PAYLOAD_APPROVAL_STATUS.REJECTED) {
      throw new BadRequestException(
          "Payload " + payloadId + " is not rejected: publish its draft as a new version.");
    }
    PayloadType payloadType = PayloadType.fromString(current.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());
    List<AttackPattern> attackPatterns =
        fromIterable(
            attackPatternRepository.findAllById(emptyIfNull(input.getAttackPatternsIds())));
    Set<Domain> domains =
        iterableToSet(domainRepository.findAllById(emptyIfNull(input.getDomainIds())));
    Set<Tag> tags = iterableToSet(tagRepository.findAllById(emptyIfNull(input.getTagIds())));
    // What the version was, for the history of what changed.
    Payload before = payloadService.copyForNewVersion(current);
    before.setVersion(current.getVersion());
    // Its action is changed in place below: keep the attack patterns it had for the history.
    List<AttackPattern> beforePatterns =
        injectorContractRepository
            .findInjectorContractByPayload(current)
            .<List<AttackPattern>>map(contract -> new ArrayList<>(contract.getAttackPatterns()))
            .orElseGet(List::of);
    payloadUtils.copyProperties(input, current);
    current.setLastModifiedBy(userService.currentUserOrNull());
    if (current instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (current instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }
    Payload saved = payloadRepository.save(current);
    // Decided before the contract is synchronised, as for a new version: the synchronisation
    // leaves the payload detached, so a later status change would not be written.
    payloadApprovalService.resubmit(saved, saved.getLastModifiedBy());
    InjectorContract contract =
        payloadService.synchroniseInjectorContractBasedOnPayload(
            saved, attackPatterns, domains, tags);
    contentHistoryService.payloadReworked(before, beforePatterns, saved);
    return new PayloadSaveResult(saved, contract, null, 0);
  }

  private PayloadSaveResult save(
      PayloadUpdateInput input,
      Payload current,
      List<AttackPattern> attackPatterns,
      boolean fromDraft) {
    PayloadType payloadType = PayloadType.fromString(current.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());
    Set<Domain> domains =
        iterableToSet(domainRepository.findAllById(emptyIfNull(input.getDomainIds())));
    Set<Tag> tags = iterableToSet(tagRepository.findAllById(emptyIfNull(input.getTagIds())));
    Optional<InjectorContract> currentContract =
        injectorContractRepository.findInjectorContractByPayload(current);

    Payload candidate = payloadService.copyForNewVersion(current);
    payloadUtils.copyProperties(input, candidate);
    // Null outside an authenticated request (system flows): an unknown modifier, never a stale one.
    candidate.setLastModifiedBy(userService.currentUserOrNull());
    if (candidate instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (candidate instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }

    Optional<PayloadVersionNumbering.Change> change =
        payloadVersionNumbering.change(
            current,
            currentContract.map(InjectorContract::getAttackPatterns).orElse(List.of()),
            candidate,
            attackPatterns);
    if (change.isEmpty()) {
      InjectorContract contract =
          payloadService.synchroniseInjectorContractBasedOnPayload(
              current, attackPatterns, domains, tags);
      return new PayloadSaveResult(current, contract, null, 0);
    }

    Payload saved =
        payloadVersionService.addVersion(
            current.getIdentity(),
            candidate,
            highest -> PayloadVersionNumbering.bump(highest.getVersion(), change.get()));
    payloadApprovalService.onWrite(saved, saved.getLastModifiedBy(), PayloadApproval.ORIGIN.UPDATE);
    InjectorContract contract =
        payloadService.synchroniseInjectorContractBasedOnPayload(
            saved, attackPatterns, domains, tags);
    currentContract.ifPresent(
        previous ->
            grantService.copyGrants(
                previous.getId(), contract.getId(), Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL));
    if (fromDraft) {
      contentHistoryService.payloadPublishedFromDraft(current, saved);
    } else {
      contentHistoryService.payloadUpdated(current, saved);
    }
    // The saver is asked to move the injects only when the new version is approved at once; a
    // pending version is offered to them by the approver who approves it.
    return payloadApprovalService
        .moveOffer(saved)
        .map(offer -> new PayloadSaveResult(saved, contract, offer.from(), offer.usages()))
        .orElseGet(() -> new PayloadSaveResult(saved, contract, current, 0));
  }
}

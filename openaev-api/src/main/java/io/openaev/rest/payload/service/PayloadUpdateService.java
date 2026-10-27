package io.openaev.rest.payload.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;

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
import io.openaev.service.content.ContentHistoryService;
import io.openaev.service.payload.PayloadVersionNumbering;
import io.openaev.service.payload.PayloadVersionService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a local payload (ADR-011): a change of its hashed content adds a new version, the previous
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

  @Transactional(rollbackFor = Exception.class)
  public PayloadSaveResult updatePayload(String payloadId, PayloadUpdateInput input) {
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
    return save(input, (Payload) Hibernate.unproxy(payload), attackPatterns);
  }

  private PayloadSaveResult save(
      PayloadUpdateInput input, Payload current, List<AttackPattern> attackPatterns) {
    PayloadType payloadType = PayloadType.fromString(current.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());
    Set<Domain> domains =
        iterableToSet(domainRepository.findAllById(emptyIfNull(input.getDomainIds())));
    Set<Tag> tags = iterableToSet(tagRepository.findAllById(emptyIfNull(input.getTagIds())));
    Optional<InjectorContract> currentContract =
        injectorContractRepository.findInjectorContractByPayload(current);

    Payload candidate = payloadService.copyForNewVersion(current);
    payloadUtils.copyProperties(input, candidate);
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
    InjectorContract contract =
        payloadService.synchroniseInjectorContractBasedOnPayload(
            saved, attackPatterns, domains, tags);
    currentContract.ifPresent(
        previous ->
            grantService.copyGrants(
                previous.getId(), contract.getId(), Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL));
    contentHistoryService.payloadUpdated(current, saved);
    int usages = currentContract.map(payloadVersionService::countTemplateUsages).orElse(0);
    return new PayloadSaveResult(saved, contract, current, usages);
  }
}

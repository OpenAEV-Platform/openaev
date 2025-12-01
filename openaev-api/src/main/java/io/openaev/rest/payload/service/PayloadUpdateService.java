package io.openaev.rest.payload.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.DomainRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.ee.Ee;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadUpdateInput;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PayloadUpdateService {

  private final PayloadUtils payloadUtils;

  private final PayloadService payloadService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;

  private final TagRepository tagRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final DomainRepository domainRepository;
  private final PayloadRepository payloadRepository;
  private final DocumentService documentService;

  @Transactional(rollbackOn = Exception.class)
  public Payload updatePayload(String payloadId, PayloadUpdateInput input) {
    if (eeService.isEnterpriseLicenseInactive(licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }

    Payload payload =
        this.payloadRepository.findById(payloadId).orElseThrow(ElementNotFoundException::new);
    List<AttackPattern> attackPatterns =
        fromIterable(attackPatternRepository.findAllById(input.getAttackPatternsIds()));
    return update(input, payload, attackPatterns);
  }

  private Payload update(
      PayloadUpdateInput input, Payload existingPayload, List<AttackPattern> attackPatterns) {
    PayloadType payloadType = PayloadType.fromString(existingPayload.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = (Payload) Hibernate.unproxy(existingPayload);
    payloadUtils.copyProperties(input, payload);

    payload.setAttackPatterns(attackPatterns);
    // Somehow, loading tags can create a detached error on detection remediation.
    // Detaching the collection before and reattaching it after bypass the issue
    List<DetectionRemediation> originalDrs = new ArrayList<>(payload.getDetectionRemediations());
    payload.setDetectionRemediations(Collections.emptyList());
    payload.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));
    payload.setDomains(iterableToSet(domainRepository.findAllById(input.getDomainIds())));
    payload.setDetectionRemediations(originalDrs);

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }

    Payload saved = payloadRepository.save(payload);
    payloadService.updateInjectorContractsForPayload(saved);
    return saved;
  }
}

package io.openbas.rest.payload.service;

import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.helper.StreamHelper.iterableToSet;
import static io.openbas.rest.payload.PayloadUtils.validateArchitecture;

import io.openbas.config.cache.LicenseCacheManager;
import io.openbas.database.model.*;
import io.openbas.database.repository.AttackPatternRepository;
import io.openbas.database.repository.PayloadRepository;
import io.openbas.database.repository.TagRepository;
import io.openbas.ee.Ee;
import io.openbas.rest.document.DocumentService;
import io.openbas.rest.exception.ElementNotFoundException;
import io.openbas.rest.payload.PayloadUtils;
import io.openbas.rest.payload.form.PayloadUpdateInput;
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

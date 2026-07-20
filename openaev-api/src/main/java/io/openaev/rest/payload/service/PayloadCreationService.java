package io.openaev.rest.payload.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;

import io.openaev.config.OpenAEVAnonymous;
import io.openaev.config.SessionHelper;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.service.UserService;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class PayloadCreationService {

  private final PayloadUtils payloadUtils;

  private final PayloadService payloadService;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;

  private final AttackPatternRepository attackPatternRepository;
  private final PayloadRepository payloadRepository;
  private final TagRepository tagRepository;
  private final DomainService domainService;
  private final DocumentService documentService;
  private final ResultsMetricCollector resultsMetricCollector;
  private final UserService userService;

  public record PayloadInjectorContractCreationResult(
      Payload payload, InjectorContract injectorContract) {}

  @Transactional(rollbackFor = Exception.class)
  public PayloadInjectorContractCreationResult createPayload(PayloadCreateInput input) {
    if (enterpriseEditionService.isEnterpriseLicenseInactive(
        licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }

    return create(input);
  }

  private PayloadInjectorContractCreationResult create(PayloadCreateInput input) {
    PayloadType payloadType = PayloadType.fromString(input.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = payloadType.getPayloadSupplier().get();
    payloadUtils.copyProperties(input, payload);

    // Manually created payloads are authored by the current user. System-driven
    // creations (startup datapacks, schedulers) have no authenticated user and
    // stay authorless.
    if (!(SessionHelper.currentUser() instanceof OpenAEVAnonymous)) {
      payload.setAuthorUser(userService.currentUser());
    }

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }

    Payload payloadSaved = payloadRepository.save(payload);
    InjectorContract injectorContract =
        payloadService.synchroniseInjectorContractBasedOnPayload(
            payloadSaved,
            fromIterable(attackPatternRepository.findAllById(input.getAttackPatternsIds())),
            iterableToSet(domainService.findAllById(input.getDomainIds())),
            iterableToSet(tagRepository.findAllById(input.getTagIds())));
    // Telemetry: one payload created, by type - recorded only once the payload and
    // its injector contract are persisted (a rollback would otherwise inflate the counter).
    resultsMetricCollector.recordPayloadCreated(payloadType.key);
    return new PayloadInjectorContractCreationResult(payloadSaved, injectorContract);
  }
}

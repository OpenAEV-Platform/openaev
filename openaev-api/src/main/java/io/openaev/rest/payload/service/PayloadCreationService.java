package io.openaev.rest.payload.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;

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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

  public record PayloadInjectorContractCreationResult(
      Payload payload, InjectorContract injectorContract) {}

  @Transactional(rollbackOn = Exception.class)
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
    return new PayloadInjectorContractCreationResult(payloadSaved, injectorContract);
  }
}

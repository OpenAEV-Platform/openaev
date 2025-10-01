package io.openaev.rest.payload.service;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.ee.Ee;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadCreateInput;
import io.openaev.service.GrantService;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class PayloadCreationService {

  private final PayloadUtils payloadUtils;

  private final PayloadService payloadService;
  private final GrantService grantService;
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;

  private final TagRepository tagRepository;
  private final AttackPatternRepository attackPatternRepository;
  private final PayloadRepository payloadRepository;
  private final DocumentService documentService;

  @Transactional(rollbackOn = Exception.class)
  public Payload createPayload(PayloadCreateInput input) {
    if (eeService.isEnterpriseLicenseInactive(licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }
    List<AttackPattern> attackPatterns =
        fromIterable(attackPatternRepository.findAllById(input.getAttackPatternsIds()));
    return create(input, attackPatterns);
  }

  private Payload create(PayloadCreateInput input, List<AttackPattern> attackPatterns) {
    PayloadType payloadType = PayloadType.fromString(input.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = payloadType.getPayloadSupplier().get();
    payloadUtils.copyProperties(input, payload);

    payload.setAttackPatterns(attackPatterns);
    payload.setTags(iterableToSet(tagRepository.findAllById(input.getTagIds())));

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

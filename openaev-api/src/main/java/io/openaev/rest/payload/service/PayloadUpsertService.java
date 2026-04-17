package io.openaev.rest.payload.service;

import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.rest.tag.TagService;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class PayloadUpsertService {

  private final PayloadUtils payloadUtils;

  private final PayloadService payloadService;
  private final EnterpriseEditionService enterpriseEditionService;
  private final LicenseCacheManager licenseCacheManager;

  private final TagService tagService;
  private final AttackPatternRepository attackPatternRepository;
  private final PayloadRepository payloadRepository;
  private final CollectorService collectorService;
  private final CollectorTypeRepository collectorTypeRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final DocumentService documentService;
  private final DomainService domainService;

  @Transactional(rollbackOn = Exception.class)
  public Payload upsertPayload(PayloadUpsertInput input) {
    Optional<Payload> payload = payloadRepository.findByExternalId(input.getExternalId());
    if (enterpriseEditionService.isEnterpriseLicenseInactive(
        licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }

    CollectorType collectorType = null;
    if (input.getCollector() != null) {
      Collector collector = this.collectorService.collector(input.getCollector());
      collectorType =
          collectorTypeRepository
              .findByName(collector.getType())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Collector type not found: " + collector.getType()));
    }
    List<AttackPattern> attackPatterns =
        attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(
            input.getAttackPatternsExternalIds(), TenantContext.getCurrentTenant());
    if (payload.isPresent()) {
      return updatePayloadFromUpsert(input, payload.get(), attackPatterns, collectorType);
    } else {
      return createPayloadFromUpsert(input, attackPatterns, collectorType);
    }
  }

  private Payload createPayloadFromUpsert(
      PayloadUpsertInput input, List<AttackPattern> attackPatterns, CollectorType collectorType) {
    PayloadType payloadType = PayloadType.fromString(input.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = payloadType.getPayloadSupplier().get();
    payloadUtils.copyProperties(input, payload, false);

    if (collectorType != null) {
      payload.setCollectorType(collectorType);
    }

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }

    Payload saved = payloadRepository.save(payload);
    payloadService.synchroniseInjectorContractBasedOnPayload(
        saved,
        attackPatterns,
        input.getDomains() != null
            ? domainService.upserts(input.getDomains(), TenantContext.getCurrentTenant())
            : new HashSet<>(
                Set.of(
                    domainService.upsert(
                        Domain.builder()
                            .name(PresetDomain.getToClassify().getName())
                            .color(PresetDomain.getToClassify().getColor())
                            .tenant(new Tenant(TenantContext.getCurrentTenant()))
                            .build()))),
        this.tagService.tagSet((input.getTagIds())));
    return saved;
  }

  public Payload updatePayloadFromUpsert(
      PayloadUpsertInput input,
      Payload existingPayload,
      List<AttackPattern> attackPatterns,
      CollectorType collectorType) {
    PayloadType payloadType = PayloadType.fromString(existingPayload.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = (Payload) Hibernate.unproxy(existingPayload);
    payloadUtils.copyProperties(input, payload, true);

    if (collectorType != null) {
      payload.setCollectorType(collectorType);
    }

    Optional<InjectorContract> existingInjectorContracts =
        injectorContractRepository.findInjectorContractByPayload(payload);
    final Set<Domain> existingDomains =
        existingInjectorContracts.isPresent()
            ? this.domainService.upsertDomainEntities(
                existingInjectorContracts.get().getDomains(), TenantContext.getCurrentTenant())
            : Set.of();
    final Set<Domain> domainsToAdd =
        this.domainService.upserts(input.getDomains(), TenantContext.getCurrentTenant());

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }

    Payload saved = payloadRepository.save(payload);
    payloadService.synchroniseInjectorContractBasedOnPayload(
        saved,
        attackPatterns,
        this.domainService.mergeDomains(
            existingDomains, domainsToAdd, new Tenant(TenantContext.getCurrentTenant())),
        this.tagService.tagSet((input.getTagIds())));
    return saved;
  }
}

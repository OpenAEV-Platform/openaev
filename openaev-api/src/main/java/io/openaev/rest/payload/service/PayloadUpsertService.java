package io.openaev.rest.payload.service;

import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;

import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.ee.Ee;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.rest.tag.TagService;
import jakarta.transaction.Transactional;
import java.time.Instant;
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
  private final Ee eeService;
  private final LicenseCacheManager licenseCacheManager;

  private final TagService tagService;
  private final AttackPatternRepository attackPatternRepository;
  private final PayloadRepository payloadRepository;
  private final CollectorService collectorService;
  private final DocumentService documentService;
  private final DomainService domainService;

  @Transactional(rollbackOn = Exception.class)
  public Payload upsertPayload(PayloadUpsertInput input) {
    Optional<Payload> payload = payloadRepository.findByExternalId(input.getExternalId());
    if (eeService.isEnterpriseLicenseInactive(licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }

    Collector collector = null;
    if (input.getCollector() != null) {
      collector = this.collectorService.collector(input.getCollector());
    }
    List<AttackPattern> attackPatterns =
        attackPatternRepository.findAllByExternalIdInIgnoreCase(
            input.getAttackPatternsExternalIds());
    if (payload.isPresent()) {
      return updatePayloadFromUpsert(input, payload.get(), attackPatterns, collector);
    } else {
      return createPayloadFromUpsert(input, attackPatterns, collector);
    }
  }

  private Payload createPayloadFromUpsert(
      PayloadUpsertInput input, List<AttackPattern> attackPatterns, Collector collector) {
    PayloadType payloadType = PayloadType.fromString(input.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = payloadType.getPayloadSupplier().get();
    payloadUtils.copyProperties(input, payload, false);

    if (collector != null) {
      payload.setCollector(collector);
    }

    payload.setDomains(
        input.getDomains() != null
            ? domainService.upserts(input.getDomains())
            : new HashSet<>(
                Set.of(
                    domainService.upsert(
                        new Domain(null, "To classify", "#FFFFFF", Instant.now(), null)))));
    payload.setAttackPatterns(attackPatterns);
    payload.setTags(this.tagService.tagSet((input.getTagIds())));

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }

    Payload saved = payloadRepository.save(payload);
    payloadService.updateInjectorContractsForPayload(saved);
    return saved;
  }

  public Payload updatePayloadFromUpsert(
      PayloadUpsertInput input,
      Payload existingPayload,
      List<AttackPattern> attackPatterns,
      Collector collector) {
    PayloadType payloadType = PayloadType.fromString(existingPayload.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = (Payload) Hibernate.unproxy(existingPayload);
    payloadUtils.copyProperties(input, payload, true);

    if (collector != null) {
      payload.setCollector(collector);
    }

    final Set<Domain> existingDomains = this.domainService.upserts(payload.getDomains());
    final Set<Domain> domainsToAdd = this.domainService.upserts(input.getDomains());
    payload.setDomains(this.domainService.mergeDomains(existingDomains, domainsToAdd));
    payload.setAttackPatterns(attackPatterns);
    payload.setTags(this.tagService.tagSet((input.getTagIds())));

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

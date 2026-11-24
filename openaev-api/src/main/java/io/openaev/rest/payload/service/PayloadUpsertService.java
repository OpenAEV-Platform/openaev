package io.openaev.rest.payload.service;

import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;

import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TxCtx;
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
import io.openaev.service.organization.OrganizationService;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  private final OrganizationService organizationService;
  private final InjectorContractRepository injectorContractRepository;
  private final DocumentService documentService;
  private final DomainService domainService;
  private final ResultsMetricCollector resultsMetricCollector;
  private final TenantWriteScopeResolver writeScopeResolver;

  @Transactional(rollbackFor = Exception.class)
  public Payload upsertPayload(TxCtx ctx, PayloadUpsertInput input) {
    // Telemetry: one payload upserted by a collector (attempts semantics).
    resultsMetricCollector.recordPayloadUpserted();
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    // A collector updates its own payloads, never a catalogue version of the same external ID.
    Optional<Payload> payload =
        payloadRepository.findByIdentityExternalIdAndIdentityRepositoryIsNull(
            input.getExternalId());
    if (enterpriseEditionService.isEnterpriseLicenseInactive(
        licenseCacheManager.getEnterpriseEditionInfo())) {
      input.setDetectionRemediations(null);
    }

    CollectorType collectorType = null;
    Organization collectorOrganization = null;
    if (input.getCollector() != null) {
      Collector collector = this.collectorService.collector(input.getCollector());
      collectorType =
          collectorTypeRepository
              .findByName(collector.getType())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "Collector type not found: " + collector.getType()));
      // A collector's payloads are authored by the collector's organization
      // (created on first use), so the arsenal can be filtered "created by
      // Atomic Red Team" etc.
      collectorOrganization = resolveCollectorOrganization(collector, tenantId);
    }
    List<AttackPattern> attackPatterns =
        attackPatternRepository.findAllByExternalIdInIgnoreCaseAndTenantId(
            input.getAttackPatternsExternalIds(), tenantId);
    if (payload.isPresent()) {
      return updatePayloadFromUpsert(
          tenantId, input, payload.get(), attackPatterns, collectorType, collectorOrganization);
    } else {
      return createPayloadFromUpsert(
          tenantId, input, attackPatterns, collectorType, collectorOrganization);
    }
  }

  /**
   * Finds or creates the {@link Organization} that authors a collector's payloads. The collector's
   * source-declared author override wins when present; otherwise the collector's display name is
   * used, keyed within the write tenant.
   */
  private Organization resolveCollectorOrganization(Collector collector, String tenantId) {
    String name =
        collector.getAuthor() != null && !collector.getAuthor().isBlank()
            ? collector.getAuthor()
            : collector.getName();
    return organizationService.findOrCreateByName(name, tenantId);
  }

  private Payload createPayloadFromUpsert(
      String tenantId,
      PayloadUpsertInput input,
      List<AttackPattern> attackPatterns,
      CollectorType collectorType,
      Organization collectorOrganization) {
    PayloadType payloadType = PayloadType.fromString(input.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Payload payload = payloadType.getPayloadSupplier().get();
    payload.setTenant(new Tenant(tenantId));
    payloadUtils.copyProperties(input, payload, false);

    if (collectorType != null) {
      payload.setCollectorType(collectorType);
    }
    if (collectorOrganization != null) {
      payload.setAuthorOrganization(collectorOrganization);
    }

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(
          documentService.documentInTenant(input.getExecutableFile(), tenantId));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.documentInTenant(input.getFileDropFile(), tenantId));
    }
    payloadUtils.validateDocumentArguments(payload, tenantId);

    Payload saved = payloadRepository.save(payload);
    payloadService.synchroniseInjectorContractBasedOnPayload(
        saved,
        attackPatterns,
        input.getDomains() != null
            ? domainService.upserts(input.getDomains(), tenantId)
            : new HashSet<>(
                Set.of(
                    domainService.upsert(
                        Domain.builder()
                            .name(PresetDomain.getToClassify().getName())
                            .color(PresetDomain.getToClassify().getColor())
                            .tenant(new Tenant(tenantId))
                            .build()))),
        this.tagService.tagSet((input.getTagIds())));
    return saved;
  }

  public Payload updatePayloadFromUpsert(
      String tenantId,
      PayloadUpsertInput input,
      Payload existingPayload,
      List<AttackPattern> attackPatterns,
      CollectorType collectorType,
      Organization collectorOrganization) {
    PayloadType payloadType = PayloadType.fromString(existingPayload.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    // Resolved from the existing payload's own tenant, not the request's write-scope tenant
    // (mirrors PayloadUpdateService#update): the upsert's lookup by external id carries no tenant
    // predicate, so the two can diverge, and an update must never attribute or link anything to a
    // tenant other than the one this payload already belongs to.
    tenantId = existingPayload.getTenant().getId();
    // Validated against the request's own arguments, before copyProperties: copyProperties sets
    // the arguments straight onto the managed entity below, so a refusal after that point would
    // still leave Hibernate's dirty checking holding the cross-tenant value to flush.
    payloadUtils.validateDocumentArguments(
        input.getArguments(), existingPayload.getArguments(), tenantId);

    Payload payload = (Payload) Hibernate.unproxy(existingPayload);
    payloadUtils.copyProperties(input, payload, true);

    if (collectorType != null) {
      payload.setCollectorType(collectorType);
    }
    if (collectorOrganization != null) {
      payload.setAuthorOrganization(collectorOrganization);
    }

    Optional<InjectorContract> existingInjectorContracts =
        injectorContractRepository.findInjectorContractByPayload(payload);
    final Set<Domain> existingDomains =
        existingInjectorContracts.isPresent()
            ? this.domainService.upsertDomainEntities(
                existingInjectorContracts.get().getDomains(), tenantId)
            : Set.of();
    final Set<Domain> domainsToAdd = this.domainService.upserts(input.getDomains(), tenantId);

    if (payload instanceof Executable executable) {
      executable.setExecutableFile(
          documentService.documentInTenant(input.getExecutableFile(), tenantId));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.documentInTenant(input.getFileDropFile(), tenantId));
    }

    Payload saved = payloadRepository.save(payload);
    payloadService.synchroniseInjectorContractBasedOnPayload(
        saved,
        attackPatterns,
        this.domainService.mergeDomains(existingDomains, domainsToAdd, new Tenant(tenantId)),
        this.tagService.tagSet((input.getTagIds())));
    return saved;
  }
}

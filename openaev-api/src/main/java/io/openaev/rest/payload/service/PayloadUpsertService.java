package io.openaev.rest.payload.service;

import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;

import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.domain.enums.PresetDomain;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.rest.tag.TagService;
import io.openaev.service.GrantService;
import io.openaev.service.content.ContentHistoryService;
import io.openaev.service.organization.OrganizationService;
import io.openaev.service.payload.PayloadVersionNumbering;
import io.openaev.service.payload.PayloadVersionService;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import io.openaev.utils.SemanticVersion;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
  private final CollectorService collectorService;
  private final CollectorTypeRepository collectorTypeRepository;
  private final OrganizationService organizationService;
  private final InjectorContractRepository injectorContractRepository;
  private final DocumentService documentService;
  private final DomainService domainService;
  private final ResultsMetricCollector resultsMetricCollector;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final PayloadVersionService payloadVersionService;
  private final PayloadVersionNumbering payloadVersionNumbering;
  private final ContentHistoryService contentHistoryService;
  private final GrantService grantService;

  /**
   * Legacy collector upsert. A catalogue payload with this external ID wins and is not touched.
   * Otherwise it writes to the one local identity of this collector and ID: a new version only when
   * the hashed content changes, the version row itself never changes (ADR-011).
   */
  @Transactional(rollbackFor = Exception.class)
  public Payload upsertPayload(TxCtx ctx, PayloadUpsertInput input) {
    // Telemetry: one payload upserted by a collector (attempts semantics).
    resultsMetricCollector.recordPayloadUpserted();
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    Optional<Payload> managed =
        payloadVersionService.findManagedIdentities(input.getExternalId()).stream()
            .map(PayloadIdentity::getLatestPayloadId)
            .filter(Objects::nonNull)
            .findFirst()
            .map(payloadVersionService::findById);
    if (managed.isPresent()) {
      return managed.get();
    }
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
    Optional<PayloadIdentity> identity = targetIdentity(collectorType, input.getExternalId());
    if (identity.isPresent()) {
      return updatePayloadFromUpsert(
          tenantId, input, identity.get(), attackPatterns, collectorOrganization);
    }
    return createPayloadFromUpsert(
        tenantId, input, attackPatterns, collectorType, collectorOrganization);
  }

  // One local identity per collector and external ID (unique index); without a collector, the one
  // local identity claiming the ID.
  private Optional<PayloadIdentity> targetIdentity(CollectorType collectorType, String externalId) {
    if (externalId == null) {
      return Optional.empty();
    }
    if (collectorType != null) {
      return payloadVersionService.findCollectorIdentity(collectorType.getId(), externalId);
    }
    List<PayloadIdentity> local =
        payloadVersionService.findLocalIdentitiesClaiming(externalId).stream()
            .filter(i -> i.getCollectorType() == null)
            .toList();
    return local.size() == 1 ? Optional.of(local.getFirst()) : Optional.empty();
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
    setFiles(input, payload);

    Payload saved =
        payloadVersionService.addVersion(
            payload.identityOrNew(),
            payload,
            PayloadVersionService.Numbering.fixed(Payload.FIRST_VERSION));
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

  private Payload updatePayloadFromUpsert(
      String tenantId,
      PayloadUpsertInput input,
      PayloadIdentity identity,
      List<AttackPattern> attackPatterns,
      Organization collectorOrganization) {
    Payload current =
        (Payload)
            Hibernate.unproxy(
                payloadVersionService.findVersions(identity).stream()
                    .max(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER))
                    .orElseThrow());
    PayloadType payloadType = PayloadType.fromString(current.getType());
    validateArchitecture(payloadType.key, input.getExecutionArch());

    Optional<InjectorContract> currentContract =
        injectorContractRepository.findInjectorContractByPayload(current);
    final Set<Domain> existingDomains =
        currentContract.isPresent()
            ? this.domainService.upsertDomainEntities(currentContract.get().getDomains(), tenantId)
            : Set.of();
    final Set<Domain> domains =
        this.domainService.mergeDomains(
            existingDomains,
            this.domainService.upserts(input.getDomains(), tenantId),
            new Tenant(tenantId));
    Set<Tag> tags = this.tagService.tagSet((input.getTagIds()));

    Payload candidate = payloadService.copyForNewVersion(current);
    payloadUtils.copyProperties(input, candidate, false);
    if (collectorOrganization != null) {
      candidate.setAuthorOrganization(collectorOrganization);
    }
    setFiles(input, candidate);

    Optional<PayloadVersionNumbering.Change> change =
        payloadVersionNumbering.change(
            current,
            currentContract.map(InjectorContract::getAttackPatterns).orElse(List.of()),
            candidate,
            attackPatterns);
    if (change.isEmpty()) {
      payloadService.synchroniseInjectorContractBasedOnPayload(
          current, attackPatterns, domains, tags);
      return current;
    }
    Payload saved =
        payloadVersionService.addVersion(
            identity,
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
    return saved;
  }

  private void setFiles(PayloadUpsertInput input, Payload payload) {
    if (payload instanceof Executable executable) {
      executable.setExecutableFile(documentService.document(input.getExecutableFile()));
    } else if (payload instanceof FileDrop fileDrop) {
      fileDrop.setFileDropFile(documentService.document(input.getFileDropFile()));
    }
  }
}

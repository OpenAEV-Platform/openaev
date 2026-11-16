package io.openaev.service.catalog.payload;

import static io.openaev.rest.payload.PayloadUtils.validateArchitecture;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.Payload;
import io.openaev.database.model.PayloadType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.catalog.CatalogHistory;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.repository.PayloadIdentityRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.domain.DomainService;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.payload.PayloadUtils;
import io.openaev.rest.payload.form.PayloadUpsertInput;
import io.openaev.rest.payload.service.PayloadService;
import io.openaev.service.catalog.event.CatalogChangePublisher;
import io.openaev.service.detection_remediation.DetectionRemediationService;
import io.openaev.service.organization.OrganizationService;
import io.openaev.service.payload.PayloadContentHashService;
import io.openaev.service.payload.PayloadUsageService;
import io.openaev.utils.catalog.SemanticVersion;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogue payloads in a tenant: each installed version sits under the identity of its catalogue
 * repository and external ID, exactly as released.
 */
@Service
@RequiredArgsConstructor
public class CatalogPayloadService {

  public static final String OTHER_TYPE_REFUSED = "A new version cannot change the payload type";

  private final PayloadIdentityRepository payloadIdentityRepository;
  private final PayloadRepository payloadRepository;
  private final PayloadUtils payloadUtils;
  private final PayloadService payloadService;
  private final PayloadContentHashService payloadContentHashService;
  private final PayloadUsageService payloadUsageService;
  private final AttackPatternService attackPatternService;
  private final DomainService domainService;
  private final OrganizationService organizationService;
  private final ObjectMapper mapper;
  private final CatalogChangePublisher changePublisher;
  private final DetectionRemediationService detectionRemediationService;

  // -- CREATE --

  /**
   * Installs a released version under its catalogue identity. Its content is read from the file the
   * release holds, and refused unless it has the hash the release gives.
   */
  @Transactional(rollbackFor = Exception.class)
  public Payload install(
      TxCtx ctx,
      String tenantId,
      InstallablePayloadVersion released,
      byte[] file,
      CatalogHistory.Mode mode) {
    PayloadIdentity identity = catalogueIdentity(tenantId, released);
    List<Payload> versions = payloadRepository.findAllByIdentityId(identity.getId());
    if (installedVersion(identity, versions, released).isPresent()) {
      throw new BadRequestException("This version is already installed");
    }
    return installVersion(ctx, tenantId, identity, versions, released, file, mode);
  }

  /** The released version, installed first when the tenant does not have it. */
  @Transactional(rollbackFor = Exception.class)
  public Payload installIfMissing(
      TxCtx ctx,
      String tenantId,
      InstallablePayloadVersion released,
      byte[] file,
      CatalogHistory.Mode mode) {
    PayloadIdentity identity = catalogueIdentity(tenantId, released);
    List<Payload> versions = payloadRepository.findAllByIdentityId(identity.getId());
    return installedVersion(identity, versions, released)
        .orElseGet(() -> installVersion(ctx, tenantId, identity, versions, released, file, mode));
  }

  // -- DELETE --

  /**
   * Hides every version of a catalogue payload that no inject runs: the catalogue offers it again,
   * and its versions are kept.
   */
  @Transactional(rollbackFor = Exception.class)
  public void uninstall(TxCtx ctx, String tenantId, String payloadId) {
    Payload payload =
        payloadRepository
            .findByIdAndTenantId(payloadId, tenantId)
            .orElseThrow(() -> new ElementNotFoundException("Payload not found"));
    PayloadIdentity identity = payload.getIdentity();
    if (!identity.isManaged() || identity.isUninstalled()) {
      throw new BadRequestException("Only an installed catalogue payload can be uninstalled");
    }
    List<Payload> versions = payloadRepository.findAllByIdentityId(identity.getId());
    payloadUsageService.refuseIfInUse(versions);
    identity.setLatestVersion(null);
    versions.forEach(
        version ->
            changePublisher.changed(
                version, CatalogHistory.Action.UNINSTALL, CatalogHistory.Mode.MANUAL));
  }

  // -- STEPS --

  private static Optional<Payload> installedVersion(
      PayloadIdentity identity, List<Payload> versions, InstallablePayloadVersion released) {
    return identity.isUninstalled() ? Optional.empty() : version(versions, released);
  }

  private static Optional<Payload> version(
      List<Payload> versions, InstallablePayloadVersion released) {
    return versions.stream().filter(v -> released.version().equals(v.getVersion())).findFirst();
  }

  private Payload installVersion(
      TxCtx ctx,
      String tenantId,
      PayloadIdentity identity,
      List<Payload> versions,
      InstallablePayloadVersion released,
      byte[] file,
      CatalogHistory.Mode mode) {
    Payload payload =
        version(versions, released)
            .orElseGet(() -> createVersion(ctx, tenantId, identity, versions, released, file));
    identity.setLatestVersion(highest(versions, payload));
    // Creating the version's contract clears the session: the identity is saved again.
    payloadIdentityRepository.save(identity);
    changePublisher.changed(payload, CatalogHistory.Action.INSTALL, mode);
    return payload;
  }

  private PayloadIdentity catalogueIdentity(String tenantId, InstallablePayloadVersion released) {
    return payloadIdentityRepository
        .findByTenantIdAndRepositoryAndExternalId(
            tenantId, released.repository(), released.externalId())
        .orElseGet(
            () -> {
              PayloadIdentity identity = new PayloadIdentity();
              identity.setTenant(new Tenant(tenantId));
              identity.setRepository(released.repository());
              identity.setExternalId(released.externalId());
              identity.setSource(Payload.PAYLOAD_SOURCE.FILIGRAN);
              return payloadIdentityRepository.save(identity);
            });
  }

  private static Payload highest(List<Payload> versions, Payload installed) {
    return Stream.concat(versions.stream(), Stream.of(installed))
        .max(Comparator.comparing(Payload::getVersion, SemanticVersion.ORDER))
        .orElseThrow();
  }

  private Payload createVersion(
      TxCtx ctx,
      String tenantId,
      PayloadIdentity identity,
      List<Payload> versions,
      InstallablePayloadVersion released,
      byte[] file) {
    PayloadUpsertInput input = read(file);
    PayloadType type = PayloadType.fromString(input.getType());
    refuseOtherType(versions, type);
    validateArchitecture(type.key, input.getExecutionArch());
    Payload payload = type.getPayloadSupplier().get();
    // Copied before the identity is set: the input carries an origin the release does not decide.
    payloadUtils.copyProperties(input, payload, false);
    payload.setTenant(new Tenant(tenantId));
    payload.setIdentity(identity);
    payload.setVersion(released.version());
    payload.setStatus(Payload.PAYLOAD_STATUS.VERIFIED);
    payload.setAuthorOrganization(
        organizationService.findOrCreateByName(CatalogItemMapper.FILIGRAN, tenantId));
    List<AttackPattern> attackPatterns = attackPatterns(ctx, input);
    payload.setContentHash(releasedHash(payload, attackPatterns, released));
    Payload saved = payloadRepository.save(payload);
    carryLocalRules(identity.getLatestVersion(), saved);
    payloadService.synchroniseInjectorContractBasedOnPayload(
        saved,
        attackPatterns,
        domainService.upserts(input.getDomains(), tenantId),
        new HashSet<>());
    return saved;
  }

  // A type change is a new external ID in the catalogue, never a new version.
  private static void refuseOtherType(List<Payload> versions, PayloadType type) {
    if (versions.stream().anyMatch(version -> version.getTypeEnum() != type)) {
      throw new BadRequestException(OTHER_TYPE_REFUSED);
    }
  }

  // The tenant only adds AI rules to a catalogue version: they stay on a newer one, to review. Read
  // before the contract is created, which clears the session.
  private void carryLocalRules(Payload replaced, Payload payload) {
    if (replaced != null
        && SemanticVersion.ORDER.compare(replaced.getVersion(), payload.getVersion()) < 0) {
      detectionRemediationService.copyAiRulesToReview(replaced, payload);
    }
  }

  private PayloadUpsertInput read(byte[] file) {
    try {
      PayloadUpsertInput input = mapper.readValue(file, PayloadUpsertInput.class);
      // Taken as written: the input's default expectations would change the content.
      if (!mapper.readTree(file).has("payload_expectations")) {
        input.setExpectations(new BaseInjectExpectation.EXPECTATION_TYPE[0]);
      }
      return input;
    } catch (IOException e) {
      throw new BadRequestException("Unreadable payload file");
    }
  }

  // A technique the tenant lacks is created from its ID; the MITRE collector completes it later.
  private List<AttackPattern> attackPatterns(TxCtx ctx, PayloadUpsertInput input) {
    return input.getAttackPatternsExternalIds().stream()
        .map(
            externalId -> {
              AttackPatternCreateInput technique = new AttackPatternCreateInput();
              technique.setExternalId(externalId);
              technique.setName(externalId);
              return attackPatternService.findOrCreate(ctx, technique);
            })
        .toList();
  }

  private String releasedHash(
      Payload payload, List<AttackPattern> attackPatterns, InstallablePayloadVersion released) {
    String hash = payloadContentHashService.hash(payload, attackPatterns).orElse(null);
    if (hash == null || !hash.equalsIgnoreCase(released.contentHash())) {
      throw new BadRequestException("This version does not match the release");
    }
    return hash;
  }
}

package io.openaev.service.catalog.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest.PayloadRef;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.repository.ScenarioIdentityRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.CatalogOffer;
import io.openaev.service.catalog.CatalogSettingsService;
import io.openaev.service.catalog.payload.CatalogPayloadOfferService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The released catalogue scenario versions a tenant can install. */
@Service
@RequiredArgsConstructor
public class CatalogScenarioOfferService implements CatalogOffer<InstallableScenarioVersion> {

  private final CatalogListService catalogListService;
  private final CatalogPayloadOfferService catalogPayloadOfferService;
  private final CatalogScenarioRequirements catalogScenarioRequirements;
  private final CatalogSettingsService catalogSettingsService;
  private final ScenarioIdentityRepository scenarioIdentityRepository;
  private final ObjectMapper mapper;

  // -- LIST --

  /**
   * A released version the catalogue still offers and this platform can run, with the payload
   * versions its release names.
   */
  @Transactional(readOnly = true)
  public InstallableScenarioVersion installable(TxCtx ctx, String externalId, String version) {
    CatalogScenario item = catalogListService.offered(CatalogScenario.class, externalId, version);
    List<MissingRequirement> missing = catalogScenarioRequirements.checker().missing(item);
    if (!missing.isEmpty()) {
      throw new BadRequestException(
          "Missing to install this scenario: "
              + missing.stream().map(MissingRequirement::label).collect(Collectors.joining(", ")));
    }
    return toInstallable(ctx, item);
  }

  /** What the installed version of a catalogue scenario lacks to run; nothing for a local one. */
  @Transactional(readOnly = true)
  public List<MissingRequirement> missingToRun(Scenario scenario) {
    if (scenario.getCatalogExternalId() == null) {
      return List.of();
    }
    return catalogListService
        .version(
            CatalogScenario.class, scenario.getCatalogExternalId(), scenario.getCatalogVersion())
        .map(item -> catalogScenarioRequirements.checkerWithStatus().missing(item))
        .orElse(List.of());
  }

  /** The latest version of every catalogue scenario the tenant does not show. */
  @Transactional(readOnly = true)
  public List<OfferedScenario> notInstalled(String tenantId) {
    Set<String> installed =
        scenarioIdentityRepository.findAllInstalledByTenantId(tenantId).stream()
            .map(CatalogOffer::key)
            .collect(Collectors.toSet());
    List<CatalogScenario> offered =
        catalogListService.latestOffered(CatalogScenario.class).stream()
            .filter(
                item ->
                    !installed.contains(
                        CatalogOffer.key(item.getRelease().getRepository(), item.getExternalId())))
            .toList();
    CatalogScenarioRequirements.Checker checker = catalogScenarioRequirements.checkerWithStatus();
    Map<String, CatalogPayload> released = releasedPayloads(offered);
    return offered.stream()
        .map(item -> new OfferedScenario(item, checker.missing(item), payloadsOf(item, released)))
        .toList();
  }

  /**
   * The latest version of every catalogue scenario the tenant never installed. A chained scenario,
   * whose scope an admin chooses, and one missing a requirement are left to install by hand.
   */
  @Override
  @Transactional(readOnly = true)
  public List<InstallableScenarioVersion> neverInstalled(TxCtx ctx, String tenantId) {
    return installableNeverInstalled(ctx, tenantId);
  }

  /** What a release brings to install by itself: the scenarios never installed, when asked. */
  @Override
  @Transactional(readOnly = true)
  public List<InstallableScenarioVersion> toAutoInstall(TxCtx ctx, String tenantId) {
    return catalogSettingsService.isAutoInstall(ctx, tenantId, ContentType.SCENARIO)
        ? installableNeverInstalled(ctx, tenantId)
        : List.of();
  }

  // -- STEPS --

  private List<InstallableScenarioVersion> installableNeverInstalled(TxCtx ctx, String tenantId) {
    // An uninstalled scenario keeps its identity: it is not installed again.
    Set<String> known =
        scenarioIdentityRepository.findAllByTenantId(tenantId).stream()
            .map(CatalogOffer::key)
            .collect(Collectors.toSet());
    CatalogScenarioRequirements.Checker checker = catalogScenarioRequirements.checker();
    return catalogListService.latestOffered(CatalogScenario.class).stream()
        .filter(
            item ->
                !known.contains(
                    CatalogOffer.key(item.getRelease().getRepository(), item.getExternalId())))
        .filter(item -> CatalogScenarioType.of(item.getScenarioType()).installedInBulk())
        .filter(item -> checker.missing(item).isEmpty())
        .map(item -> toInstallable(ctx, item))
        .toList();
  }

  private InstallableScenarioVersion toInstallable(TxCtx ctx, CatalogScenario item) {
    return new InstallableScenarioVersion(
        item.getRelease().getRepository(),
        item.getExternalId(),
        item.getVersion(),
        CatalogScenarioType.of(item.getScenarioType()),
        item.getAuthor(),
        item.getArchiveHash(),
        payloadRefs(item).stream()
            .map(
                ref -> catalogPayloadOfferService.installable(ctx, ref.externalId(), ref.version()))
            .toList());
  }

  // Every version of the payloads these releases name, read at once.
  private Map<String, CatalogPayload> releasedPayloads(List<CatalogScenario> items) {
    Set<String> externalIds =
        items.stream()
            .flatMap(item -> payloadRefs(item).stream())
            .map(PayloadRef::externalId)
            .collect(Collectors.toSet());
    if (externalIds.isEmpty()) {
      return Map.of();
    }
    return catalogListService.versions(CatalogPayload.class, externalIds).stream()
        .collect(
            Collectors.toMap(
                payload -> versionKey(payload.getExternalId(), payload.getVersion()),
                Function.identity(),
                (first, other) -> first));
  }

  private List<CatalogPayload> payloadsOf(
      CatalogScenario item, Map<String, CatalogPayload> released) {
    return payloadRefs(item).stream()
        .map(ref -> released.get(versionKey(ref.externalId(), ref.version())))
        .filter(Objects::nonNull)
        .toList();
  }

  private static String versionKey(String externalId, String version) {
    return externalId + "@" + version;
  }

  private List<PayloadRef> payloadRefs(CatalogScenario item) {
    if (item.getPayloadRefs() == null) {
      return List.of();
    }
    try {
      return mapper.readValue(item.getPayloadRefs(), new TypeReference<>() {});
    } catch (IOException e) {
      throw new IllegalStateException("Unreadable payload references", e);
    }
  }
}

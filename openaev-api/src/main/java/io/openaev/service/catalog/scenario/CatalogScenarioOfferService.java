package io.openaev.service.catalog.scenario;

import static io.openaev.database.specification.CatalogItemSpecification.scenarioInstalled;
import static io.openaev.database.specification.CatalogItemSpecification.scenarioNotInstalled;
import static io.openaev.database.specification.CatalogItemSpecification.scenarioTypeIn;
import static io.openaev.database.specification.CatalogItemSpecification.scenarioUpdateAvailable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest.PayloadRef;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.catalog.CatalogItem;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.database.specification.CatalogItemSpecification;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.catalog.CatalogFacet;
import io.openaev.service.catalog.CatalogFacetCounter;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.CatalogOffer;
import io.openaev.service.catalog.CatalogSettingsService;
import io.openaev.service.catalog.payload.CatalogPayloadOfferService;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The released catalogue scenario versions a tenant can install. */
@Service
@RequiredArgsConstructor
public class CatalogScenarioOfferService implements CatalogOffer<InstallableScenarioVersion> {

  private static final String INSTALLED = "INSTALLED";
  private static final String NOT_INSTALLED = "NOT_INSTALLED";
  private static final String UPDATE_AVAILABLE = "UPDATE_AVAILABLE";

  private final CatalogListService catalogListService;
  private final CatalogPayloadOfferService catalogPayloadOfferService;
  private final CatalogScenarioRequirements catalogScenarioRequirements;
  private final CatalogSettingsService catalogSettingsService;
  private final CatalogScenarioService catalogScenarioService;
  private final CatalogFacetCounter catalogFacetCounter;
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

  /** A released version, offered or not, as the one a tenant installed. */
  @Transactional(readOnly = true)
  public ReleasedScenario released(TxCtx ctx, String externalId, String version) {
    return toReleased(
        catalogListService
            .version(CatalogScenario.class, externalId, version)
            .orElseThrow(() -> new ElementNotFoundException("Catalogue version not found")));
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

  // -- SEARCH --

  /**
   * A page of the offered scenarios, with what each lacks, its payloads and the scenario installed
   * from it.
   */
  @Transactional(readOnly = true)
  public Page<OfferedScenario> search(String tenantId, SearchPaginationInput input) {
    Page<CatalogScenario> page =
        catalogListService.search(
            CatalogScenario.class, latestOffered(tenantId), input, facets(tenantId));
    CatalogScenarioRequirements.Checker checker = catalogScenarioRequirements.checkerWithStatus();
    Map<String, CatalogPayload> released = releasedPayloads(page.getContent());
    Map<String, String> installed =
        catalogScenarioService.installedScenarioIds(
            tenantId, page.getContent().stream().map(CatalogItem::getExternalId).toList());
    return page.map(
        item ->
            new OfferedScenario(
                item,
                checker.missing(item),
                payloadsOf(item, released),
                installed.get(CatalogOffer.key(item))));
  }

  @Transactional(readOnly = true)
  public Map<String, Map<String, Long>> facetCounts(String tenantId, SearchPaginationInput input) {
    return catalogFacetCounter.count(
        CatalogScenario.class, latestOffered(tenantId), input, facets(tenantId));
  }

  // -- STEPS --

  private static Specification<CatalogItem> latestOffered(String tenantId) {
    return CatalogItemSpecification.latestOffered(tenantId, CatalogScenario.class);
  }

  private static List<CatalogFacet> facets(String tenantId) {
    Map<String, Specification<CatalogItem>> installation = new LinkedHashMap<>();
    installation.put(INSTALLED, scenarioInstalled(tenantId));
    installation.put(NOT_INSTALLED, scenarioNotInstalled(tenantId));
    installation.put(
        UPDATE_AVAILABLE,
        scenarioUpdateAvailable(tenantId)
            .and(scenarioTypeIn(CatalogScenarioType.updatableNames())));
    return List.of(
        new CatalogFacet.Condition(CatalogFacet.INSTALLATION_KEY, installation),
        new CatalogFacet.Grouped(CatalogFacet.SCENARIO_TYPE_KEY),
        CatalogFacet.PLATFORMS,
        new CatalogFacet.Grouped(CatalogFacet.CATEGORY_KEY),
        new CatalogFacet.Grouped(CatalogFacet.AUTHOR_KEY));
  }

  private List<InstallableScenarioVersion> installableNeverInstalled(TxCtx ctx, String tenantId) {
    CatalogScenarioRequirements.Checker checker = catalogScenarioRequirements.checker();
    return catalogListService
        .latestOfferedExcept(CatalogScenario.class, catalogScenarioService.knownKeys(tenantId))
        .stream()
        .filter(item -> CatalogScenarioType.of(item.getScenarioType()).installedInBulk())
        .filter(item -> checker.missing(item).isEmpty())
        .map(item -> toInstallable(ctx, item))
        .toList();
  }

  private InstallableScenarioVersion toInstallable(TxCtx ctx, CatalogScenario item) {
    ReleasedScenario released = toReleased(item);
    return new InstallableScenarioVersion(
        item.getRelease().getRepository(),
        item.getExternalId(),
        item.getVersion(),
        CatalogScenarioType.of(item.getScenarioType()),
        item.getAuthor(),
        released.archiveHash(),
        released.payloads().stream()
            .map(
                ref -> catalogPayloadOfferService.installable(ctx, ref.externalId(), ref.version()))
            .toList());
  }

  private ReleasedScenario toReleased(CatalogScenario item) {
    return new ReleasedScenario(item.getArchiveHash(), payloadRefs(item));
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

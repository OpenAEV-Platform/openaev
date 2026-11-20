package io.openaev.service.catalog.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest.PayloadRef;
import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.database.model.catalog.ContentType;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.CatalogSettingsService;
import io.openaev.service.catalog.payload.CatalogPayloadOfferService;
import java.io.IOException;
import java.util.List;
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
  private final CatalogScenarioService catalogScenarioService;
  private final ObjectMapper mapper;

  // -- LIST --

  /**
   * A released version the catalogue still offers and this platform can run, with the payload
   * versions its release names.
   */
  @Transactional(readOnly = true)
  public InstallableScenarioVersion installable(TxCtx ctx, String externalId, String version) {
    CatalogScenario item = catalogListService.offered(CatalogScenario.class, externalId, version);
    List<String> missing = catalogScenarioRequirements.missing(item);
    if (!missing.isEmpty()) {
      throw new BadRequestException(
          "Missing to install this scenario: " + String.join(", ", missing));
    }
    return toInstallable(ctx, item);
  }

  /** The latest version of every catalogue scenario the tenant does not show. */
  @Transactional(readOnly = true)
  public List<OfferedScenario> notInstalled(String tenantId) {
    return catalogListService
        .latestOfferedExcept(CatalogScenario.class, catalogScenarioService.installedKeys(tenantId))
        .stream()
        .map(item -> new OfferedScenario(item, catalogScenarioRequirements.missing(item)))
        .toList();
  }

  /**
   * The latest version of every catalogue scenario the tenant never installed. A chained scenario,
   * whose scope an admin chooses, and one missing a requirement are left to install by hand.
   */
  @Override
  @Transactional(readOnly = true)
  public List<InstallableScenarioVersion> neverInstalled(TxCtx ctx, String tenantId) {
    return catalogListService
        .latestOfferedExcept(CatalogScenario.class, catalogScenarioService.knownKeys(tenantId))
        .stream()
        .filter(item -> CatalogScenarioType.of(item.getScenarioType()).installedInBulk())
        .filter(item -> catalogScenarioRequirements.missing(item).isEmpty())
        .map(item -> toInstallable(ctx, item))
        .toList();
  }

  /** What a release brings to install by itself: the scenarios never installed, when asked. */
  @Override
  @Transactional(readOnly = true)
  public List<InstallableScenarioVersion> toAutoInstall(TxCtx ctx, String tenantId) {
    return catalogSettingsService.isAutoInstall(ctx, tenantId, ContentType.SCENARIO)
        ? neverInstalled(ctx, tenantId)
        : List.of();
  }

  // -- STEPS --

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

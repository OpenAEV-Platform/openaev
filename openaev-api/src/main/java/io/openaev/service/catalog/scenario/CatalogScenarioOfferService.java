package io.openaev.service.catalog.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest.PayloadRef;
import io.openaev.context.TxCtx;
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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The released catalogue scenario versions a tenant can install. */
@Service
@RequiredArgsConstructor
public class CatalogScenarioOfferService implements CatalogOffer<InstallableScenarioVersion> {

  public static final String CHAINED_REFUSED = "A chained scenario cannot be installed yet";
  private static final String CHAINED = "chained";

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
    refusal(item)
        .ifPresent(
            reason -> {
              throw new BadRequestException(reason);
            });
    return toInstallable(ctx, item);
  }

  /**
   * The latest version of every catalogue scenario the tenant never installed. A scenario this
   * platform cannot install as is (chained, missing a requirement) is left to install by hand.
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
    return catalogListService.latestOffered(CatalogScenario.class).stream()
        .filter(
            item ->
                !known.contains(
                    CatalogOffer.key(item.getRelease().getRepository(), item.getExternalId())))
        .filter(item -> refusal(item).isEmpty())
        .map(item -> toInstallable(ctx, item))
        .toList();
  }

  private Optional<String> refusal(CatalogScenario item) {
    if (CHAINED.equalsIgnoreCase(item.getScenarioType())) {
      return Optional.of(CHAINED_REFUSED);
    }
    List<String> missing = catalogScenarioRequirements.missing(item);
    return missing.isEmpty()
        ? Optional.empty()
        : Optional.of("Missing to install this scenario: " + String.join(", ", missing));
  }

  private InstallableScenarioVersion toInstallable(TxCtx ctx, CatalogScenario item) {
    return new InstallableScenarioVersion(
        item.getRelease().getRepository(),
        item.getExternalId(),
        item.getVersion(),
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

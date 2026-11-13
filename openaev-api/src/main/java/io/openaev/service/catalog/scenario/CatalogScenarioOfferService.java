package io.openaev.service.catalog.scenario;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest.PayloadRef;
import io.openaev.context.TxCtx;
import io.openaev.database.model.catalog.CatalogScenario;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.catalog.CatalogListService;
import io.openaev.service.catalog.payload.CatalogPayloadOfferService;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The released catalogue scenario versions a tenant can install. */
@Service
@RequiredArgsConstructor
public class CatalogScenarioOfferService {

  public static final String CHAINED_REFUSED = "A chained scenario cannot be installed yet";
  private static final String CHAINED = "chained";

  private final CatalogListService catalogListService;
  private final CatalogPayloadOfferService catalogPayloadOfferService;
  private final CatalogScenarioRequirements catalogScenarioRequirements;
  private final ObjectMapper mapper;

  // -- LIST --

  /**
   * A released version the catalogue still offers and this platform can run, with the payload
   * versions its release names.
   */
  @Transactional(readOnly = true)
  public InstallableScenarioVersion installable(TxCtx ctx, String externalId, String version) {
    CatalogScenario item = catalogListService.offered(CatalogScenario.class, externalId, version);
    if (CHAINED.equalsIgnoreCase(item.getScenarioType())) {
      throw new BadRequestException(CHAINED_REFUSED);
    }
    List<String> missing = catalogScenarioRequirements.missing(item);
    if (!missing.isEmpty()) {
      throw new BadRequestException(
          "Missing to install this scenario: " + String.join(", ", missing));
    }
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

  // -- STEPS --

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

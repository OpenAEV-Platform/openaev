package io.openaev.api.catalog;

import io.openaev.api.catalog.dto.action.AvailableActionOutput;
import io.openaev.api.catalog.dto.action.AvailableIntegrationActionOutput;
import io.openaev.api.catalog.dto.action.AvailablePayloadActionOutput;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.catalog.CatalogPayload;
import java.util.List;
import java.util.Map;

public class AvailableActionMapper {

  // The catalogue publishes payload names in English.
  private static final String PAYLOAD_NAME_LANGUAGE = "en";

  private AvailableActionMapper() {}

  public static AvailableActionOutput toOutput(CatalogPayload payload) {
    return new AvailablePayloadActionOutput(
        payload.getExternalId(),
        payload.getName() == null ? Map.of() : Map.of(PAYLOAD_NAME_LANGUAGE, payload.getName()),
        payload.getDomain(),
        payload.getPlatforms() == null ? List.of() : List.of(payload.getPlatforms()),
        payload.getVersion(),
        payload.getDescription(),
        payload.getPublishedAt());
  }

  public static AvailableActionOutput toOutput(CatalogConnectorInjectorContract contract) {
    CatalogConnector integration = contract.getCatalogConnector();
    return new AvailableIntegrationActionOutput(
        contract.getExternalId(),
        contract.getLabels(),
        CatalogItemMapper.primaryDomain(contract.getDomains()),
        contract.getPlatforms(),
        contract.getAttackPatterns(),
        integration.getId(),
        integration.getTitle(),
        integration.getLogoUrl());
  }
}

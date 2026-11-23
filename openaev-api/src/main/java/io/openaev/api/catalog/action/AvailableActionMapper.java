package io.openaev.api.catalog.action;

import io.openaev.api.catalog.CatalogItemMapper;
import io.openaev.api.catalog.action.dto.AvailableActionOutput;
import io.openaev.api.catalog.action.dto.AvailableIntegrationActionOutput;
import io.openaev.api.catalog.action.dto.AvailablePayloadActionOutput;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.CatalogConnectorInjectorContract;
import io.openaev.database.model.catalog.CatalogPayload;
import io.openaev.service.catalog.action.AvailableActions;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

public class AvailableActionMapper {

  // The catalogue publishes payload names in English.
  private static final String PAYLOAD_NAME_LANGUAGE = "en";

  private AvailableActionMapper() {}

  public static Page<AvailableActionOutput> toPage(AvailableActions actions) {
    return new PageImpl<>(
        Stream.concat(
                actions.payloads().stream().map(AvailableActionMapper::toOutput),
                actions.contracts().stream().map(AvailableActionMapper::toOutput))
            .toList(),
        actions.pageable(),
        actions.total());
  }

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

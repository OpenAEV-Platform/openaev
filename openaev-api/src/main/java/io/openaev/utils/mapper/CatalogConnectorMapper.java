package io.openaev.utils.mapper;

import io.openaev.database.model.CatalogConnector;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorOutput;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorSimpleOutput;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class CatalogConnectorMapper {

  public CatalogConnectorOutput toCatalogConnectorOutput(
      CatalogConnector catalogConnector, Integer instanceDeployedCount) {
    return CatalogConnectorOutput.builder()
        .id(catalogConnector.getId())
        .slug(catalogConnector.getSlug())
        .title(catalogConnector.getTitle())
        .description(catalogConnector.getDescription())
        .shortDescription(catalogConnector.getShortDescription())
        .logoUrl(catalogConnector.getLogoUrl())
        .isVerified(catalogConnector.isVerified())
        .lastVerifiedDate(catalogConnector.getLastVerifiedDate())
        .subscriptionLink(catalogConnector.getSubscriptionLink())
        .sourceCode(catalogConnector.getSourceCode())
        .containerType(catalogConnector.getContainerType())
        .useCases(catalogConnector.getUseCases())
        .isManagerSupported(catalogConnector.isManagerSupported())
        .instanceDeployedCount(instanceDeployedCount)
        .build();
  }

  public CatalogConnectorSimpleOutput toCatalogSimpleOutput(
      @Nullable CatalogConnector catalogConnector) {
    if (catalogConnector == null) return null;
    return CatalogConnectorSimpleOutput.builder()
        .id(catalogConnector.getId())
        .shortDescription(catalogConnector.getShortDescription())
        .logoUrl(catalogConnector.getLogoUrl())
        .build();
  }

  public ConnectorIds toConnectorIds(String catalogConnectorId, String connectorInstanceId) {
    return ConnectorIds.builder()
        .catalogConnectorId(catalogConnectorId)
        .connectorInstanceId(connectorInstanceId)
        .build();
  }
}

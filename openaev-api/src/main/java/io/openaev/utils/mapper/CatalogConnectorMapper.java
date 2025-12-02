package io.openaev.utils.mapper;

import io.openaev.database.model.CatalogConnector;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class CatalogConnectorMapper {

  public CatalogConnectorOutput toCatalogConnectorOutput(CatalogConnector catalogConnector) {
    return CatalogConnectorOutput.builder()
        .id(catalogConnector.getId())
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
        .build();
  }
}

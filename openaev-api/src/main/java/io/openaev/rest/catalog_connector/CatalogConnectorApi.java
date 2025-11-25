package io.openaev.rest.catalog_connector;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.CatalogConnectorService;
import io.openaev.service.FileService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CatalogConnectorApi extends RestBehavior {
  public static final String CATALOG_CONNECTOR_URI = "/api/connectors";
  private final CatalogConnectorService catalogConnectorService;
  private final FileService fileService;

  @GetMapping(CATALOG_CONNECTOR_URI)
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.CATALOG)
  public List<CatalogConnector> getCatalogConnectors() {
    return this.catalogConnectorService.catalogConnectors();
  }

  @GetMapping(CATALOG_CONNECTOR_URI + "/{connectorId}")
  @RBAC(
      resourceId = "#connectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.CATALOG)
  public CatalogConnector getConnector(@PathVariable String connectorId) {
    return catalogConnectorService
        .findById(connectorId)
        .orElseThrow(
            () -> new ElementNotFoundException("Connector not found with id: " + connectorId));
  }

  @GetMapping(
      value = "/api/images/catalog/connectors/logos/{fileName}",
      produces = MediaType.IMAGE_PNG_VALUE)
  @RBAC(skipRBAC = true)
  public ResponseEntity<byte[]> getCatalogLogo(@PathVariable String fileName) throws IOException {
    Optional<InputStream> fileStream = fileService.getCatalogConnectorImage(fileName);

    if (fileStream.isPresent()) {
      byte[] bytes = IOUtils.toByteArray(fileStream.get());
      return ResponseEntity.ok().cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES)).body(bytes);
    }

    return ResponseEntity.notFound().build();
  }
}

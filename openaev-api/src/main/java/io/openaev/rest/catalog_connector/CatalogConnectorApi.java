package io.openaev.rest.catalog_connector;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.CatalogConnectorConfiguration;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.catalog_connector.dto.CatalogConnectorOutput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CatalogConnectorApi extends RestBehavior {
  public static final String CATALOG_CONNECTOR_URI = "/api/catalog-connector";
  public static final String TENANT_CATALOG_CONNECTOR_URI = TENANT_PREFIX + "/catalog-connector";
  private static final String CATALOG_CONNECTOR_LOGO_URI =
      "/api/images/catalog/connectors/logos/{fileName}";

  private final CatalogConnectorService catalogConnectorService;
  private final FileService fileService;

  @GetMapping({CATALOG_CONNECTOR_URI, TENANT_CATALOG_CONNECTOR_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.CATALOG)
  // TxCtx is resolved from the request and applied by the transaction aspect; it scopes the
  // instance_deployed_count computation (which reads connector_instances) to the caller's
  // tenants. The handler does not use it directly.
  public List<CatalogConnectorOutput> getCatalogConnectors(TxCtx ctx) {
    return this.catalogConnectorService.getCatalogConnectors();
  }

  @GetMapping({
    CATALOG_CONNECTOR_URI + "/{catalogConnectorId}",
    TENANT_CATALOG_CONNECTOR_URI + "/{catalogConnectorId}"
  })
  @Transactional
  @AccessControl(
      resourceId = "#catalogConnectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.CATALOG)
  // TxCtx is resolved from the request and applied by the transaction aspect; it scopes the
  // instance_deployed_count computation (which reads connector_instances) to the caller's
  // tenants. The handler does not use it directly.
  public CatalogConnectorOutput getConnector(TxCtx ctx, @PathVariable String catalogConnectorId) {
    return this.catalogConnectorService.catalogConnectorOutput(catalogConnectorId);
  }

  @GetMapping(value = CATALOG_CONNECTOR_LOGO_URI, produces = MediaType.IMAGE_PNG_VALUE)
  @Transactional
  @AccessControl(skipRBAC = true)
  public ResponseEntity<byte[]> getCatalogLogo(TxCtx ctx, @PathVariable String fileName)
      throws IOException {
    Optional<InputStream> fileStream = fileService.getCatalogConnectorImage(fileName);

    if (fileStream.isPresent()) {
      byte[] bytes = IOUtils.toByteArray(fileStream.get());
      return ResponseEntity.ok().cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES)).body(bytes);
    }

    return ResponseEntity.notFound().build();
  }

  @GetMapping({
    CATALOG_CONNECTOR_URI + "/{catalogConnectorId}/configurations",
    TENANT_CATALOG_CONNECTOR_URI + "/{catalogConnectorId}/configurations"
  })
  @Transactional
  @AccessControl(
      resourceId = "#catalogConnectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.CATALOG)
  public Set<CatalogConnectorConfiguration> getCatalogConnectorConfigurations(
      TxCtx ctx, @PathVariable String catalogConnectorId) {
    return catalogConnectorService.getCatalogConnectorConfigurations(catalogConnectorId);
  }
}

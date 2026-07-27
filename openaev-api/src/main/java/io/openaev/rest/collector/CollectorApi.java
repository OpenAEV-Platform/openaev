package io.openaev.rest.collector;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.config.RequireTenantSelector;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Collector;
import io.openaev.database.model.ConnectorCompositeId;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.rest.catalog_connector.dto.ConnectorIds;
import io.openaev.rest.collector.form.CollectorCreateInput;
import io.openaev.rest.collector.form.CollectorOutput;
import io.openaev.rest.collector.form.CollectorUpdateInput;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.FileService;
import io.openaev.service.exception.ConnectorStatusException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class CollectorApi extends RestBehavior {
  public static final String COLLECTOR_URI = "/api/collectors";
  private static final String TENANT_COLLECTOR_URI = TENANT_PREFIX + "/collectors";
  private final CollectorService collectorService;
  private final CollectorRepository collectorRepository;
  private final SecurityPlatformRepository securityPlatformRepository;
  private final TenantWriteScopeResolver writeScopeResolver;

  private final FileService fileService;

  @GetMapping({COLLECTOR_URI, TENANT_COLLECTOR_URI})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.COLLECTOR)
  @Operation(
      summary = "Retrieve collectors",
      description = "Retrieve all collectors and pending collectors if includeNext is true")
  @Transactional(readOnly = true)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = "application/json",
              array = @ArraySchema(schema = @Schema(implementation = CollectorOutput.class))))
  // TxCtx is resolved from the request and applied by the transaction aspect; it scopes this read
  // to the caller's tenants. The handler does not use it directly.
  public Iterable<CollectorOutput> collectors(
      TxCtx ctx,
      @Parameter(
              name = "includeNext",
              description = "Include collectors pending deployment",
              required = false)
          @RequestParam(value = "include_next", required = false, defaultValue = "false")
          boolean includeNext) {
    return collectorService.collectorsOutput(includeNext);
  }

  private Collector applyCollectorUpdate(
      Collector collector,
      String type,
      String name,
      int period,
      Instant lastExecution,
      String securityPlatform) {
    collector.setUpdatedAt(Instant.now());
    collector.setExternal(true);
    collector.setType(type);
    collector.setName(name);
    collector.setPeriod(period);
    collector.setLastExecution(lastExecution);
    if (securityPlatform != null) {
      collector.setSecurityPlatform(
          securityPlatformRepository.findById(securityPlatform).orElseThrow());
    }
    return collectorRepository.save(collector);
  }

  @GetMapping({COLLECTOR_URI + "/{collectorId}", TENANT_COLLECTOR_URI + "/{collectorId}"})
  @Transactional
  @AccessControl(
      resourceId = "#collectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.COLLECTOR)
  // Collector uses a composite PK (collector_id, tenant_id); the same ID exists per tenant.
  // Require a selector so findByCollectorId never returns multiple rows under a multi-tenant scope.
  public Collector getCollector(
      @RequireTenantSelector TxCtx ctx, @PathVariable String collectorId) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return collectorService.collector(collectorId, tenantId);
  }

  @GetMapping({
    COLLECTOR_URI + "/{collectorId}/related-ids",
    TENANT_COLLECTOR_URI + "/{collectorId}/related-ids"
  })
  @AccessControl(
      resourceId = "#collectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.COLLECTOR)
  @Operation(summary = "Retrieve collector related ids")
  @Transactional
  // Composite PK: require a tenant selector to avoid NonUniqueResultException on the ID lookup.
  public ConnectorIds getCollectorRelatedIds(
      @RequireTenantSelector TxCtx ctx, @PathVariable String collectorId) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return collectorService.getCollectorRelationsId(collectorId, tenantId);
  }

  // -- IMAGE --

  @GetMapping(
      value = {
        COLLECTOR_URI + "/{collectorType}/image",
        TENANT_COLLECTOR_URI + "/{collectorType}/image"
      },
      produces = MediaType.IMAGE_PNG_VALUE)
  @AccessControl(skipRBAC = true)
  @Operation(summary = "Get collector image by type")
  @Transactional
  public ResponseEntity<byte[]> getCollectorImage(@PathVariable String collectorType)
      throws IOException {
    Optional<InputStream> fileStream = fileService.getCollectorImage(collectorType);
    if (fileStream.isPresent()) {
      try (InputStream is = fileStream.get()) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
            .body(IOUtils.toByteArray(is));
      }
    }
    return ResponseEntity.notFound().build();
  }

  @GetMapping(
      value = {
        COLLECTOR_URI + "/id/{collectorId}/image",
        TENANT_COLLECTOR_URI + "/id/{collectorId}/image"
      },
      produces = MediaType.IMAGE_PNG_VALUE)
  @AccessControl(skipRBAC = true)
  @Operation(summary = "Get collector image by collector id")
  @Transactional
  // Composite PK: require a tenant selector to avoid NonUniqueResultException on the ID lookup.
  public ResponseEntity<byte[]> getCollectorImageById(
      @RequireTenantSelector TxCtx ctx, @PathVariable String collectorId) throws IOException {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    Optional<Collector> collector =
        collectorRepository.findById(ConnectorCompositeId.of(collectorId, tenantId));
    if (collector.isEmpty()) {
      return ResponseEntity.notFound().build();
    }
    Optional<InputStream> fileStream = fileService.getCollectorImage(collector.get().getType());
    if (fileStream.isPresent()) {
      try (InputStream is = fileStream.get()) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
            .body(IOUtils.toByteArray(is));
      }
    }
    return ResponseEntity.notFound().build();
  }

  @PutMapping({COLLECTOR_URI + "/{collectorId}", TENANT_COLLECTOR_URI + "/{collectorId}"})
  @AccessControl(
      resourceId = "#collectorId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.COLLECTOR)
  @Transactional(rollbackFor = Exception.class)
  // Composite PK: require a tenant selector so the update targets exactly one row.
  public Collector updateCollector(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable String collectorId,
      @Valid @RequestBody CollectorUpdateInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    Collector collector = collectorService.collector(collectorId, tenantId);
    return applyCollectorUpdate(
        collector,
        collector.getType(),
        collector.getName(),
        collector.getPeriod(),
        input.getLastExecution(),
        collector.getSecurityPlatform() != null ? collector.getSecurityPlatform().getId() : null);
  }

  @DeleteMapping({COLLECTOR_URI + "/{collectorId}", TENANT_COLLECTOR_URI + "/{collectorId}"})
  @AccessControl(
      resourceId = "#collectorId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.COLLECTOR)
  @Operation(
      summary = "Delete a collector",
      description =
          "Removes a registered collector. Intended for stopped collectors that no longer ping;"
              + " an active collector re-registers on its next heartbeat.")
  @Transactional(rollbackFor = Exception.class)
  public void deleteCollector(@RequireTenantSelector TxCtx ctx, @PathVariable String collectorId) {
    // Enforce a single-tenant write scope (400 on ambiguous selector) before issuing the delete.
    writeScopeResolver.tenantForWrite(ctx, null);
    collectorRepository.deleteByCollectorId(collectorId);
  }

  @PostMapping(
      value = {COLLECTOR_URI, TENANT_COLLECTOR_URI},
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.COLLECTOR)
  @Transactional(rollbackFor = Exception.class)
  public Collector registerCollector(
      TxCtx ctx,
      @Valid @RequestPart("input") CollectorCreateInput input,
      @RequestPart("icon") Optional<MultipartFile> file) {
    try {
      String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
      InputStream iconStream =
          file.isPresent() && "image/png".equals(file.get().getContentType())
              ? file.get().getInputStream()
              : null;
      return collectorService.register(
          tenantId,
          input.getId(),
          input.getType(),
          input.getName(),
          true,
          input.getPeriod(),
          input.getSecurityPlatform(),
          iconStream,
          input.getAuthor());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

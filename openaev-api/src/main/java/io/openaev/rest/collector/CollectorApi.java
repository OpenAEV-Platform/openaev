package io.openaev.rest.collector;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.Collector;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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

  private final FileService fileService;

  @GetMapping({COLLECTOR_URI, TENANT_COLLECTOR_URI})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.COLLECTOR)
  @Operation(
      summary = "Retrieve collectors",
      description = "Retrieve all collectors and pending collectors if includeNext is true")
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = "application/json",
              array = @ArraySchema(schema = @Schema(implementation = CollectorOutput.class))))
  public Iterable<CollectorOutput> collectors(
      @Parameter(
              name = "includeNext",
              description = "Include collectors pending deployment",
              required = false)
          @RequestParam(value = "include_next", required = false, defaultValue = "false")
          boolean includeNext) {
    return collectorService.collectorsOutput(includeNext);
  }

  private Collector updateCollector(
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
  @AccessControl(
      resourceId = "#collectorId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.COLLECTOR)
  public Collector getCollector(@PathVariable String collectorId) {
    return collectorService.collector(collectorId);
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
  public ConnectorIds getCollectorRelatedIds(@PathVariable String collectorId) {
    return collectorService.getCollectorRelationsId(collectorId);
  }

  @PutMapping({COLLECTOR_URI + "/{collectorId}", TENANT_COLLECTOR_URI + "/{collectorId}"})
  @AccessControl(
      resourceId = "#collectorId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.COLLECTOR)
  @Transactional(rollbackOn = Exception.class)
  public Collector updateCollector(
      @PathVariable String collectorId, @Valid @RequestBody CollectorUpdateInput input) {
    Collector collector = collectorService.collector(collectorId);
    return updateCollector(
        collector,
        collector.getType(),
        collector.getName(),
        collector.getPeriod(),
        input.getLastExecution(),
        collector.getSecurityPlatform() != null ? collector.getSecurityPlatform().getId() : null);
  }

  @PostMapping(
      value = {COLLECTOR_URI, TENANT_COLLECTOR_URI},
      produces = {MediaType.APPLICATION_JSON_VALUE},
      consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.COLLECTOR)
  @Transactional(rollbackOn = Exception.class)
  public Collector registerCollector(
      @Valid @RequestPart("input") CollectorCreateInput input,
      @RequestPart("icon") Optional<MultipartFile> file) {
    try {
      InputStream iconStream =
          file.isPresent() && "image/png".equals(file.get().getContentType())
              ? file.get().getInputStream()
              : null;
      return collectorService.register(
          input.getId(),
          input.getType(),
          input.getName(),
          true,
          input.getPeriod(),
          input.getSecurityPlatform(),
          iconStream);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}

package io.openaev.rest.payload;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.payload.form.*;
import io.openaev.rest.payload.output.PayloadOutput;
import io.openaev.rest.payload.service.*;
import io.openaev.utils.mapper.PayloadMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class PayloadApi extends RestBehavior {

  public static final String PAYLOAD_URI = "/api/payloads";
  public static final String TENANT_PAYLOAD_URI = TENANT_PREFIX + "/payloads";

  private final PayloadRepository payloadRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final PayloadService payloadService;
  private final PayloadCreationService payloadCreationService;
  private final PayloadUpdateService payloadUpdateService;
  private final PayloadUpsertService payloadUpsertService;
  private final DocumentService documentService;
  private final CollectorService collectorsService;
  private final PayloadMapper payloadMapper;

  @PostMapping({PAYLOAD_URI + "/search", TENANT_PAYLOAD_URI + "/search"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.PAYLOAD)
  public Page<Payload> payloads(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return this.payloadService.searchPayloads(searchPaginationInput);
  }

  @GetMapping({PAYLOAD_URI + "/{payloadId}", TENANT_PAYLOAD_URI + "/{payloadId}"})
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PAYLOAD)
  public PayloadOutput payload(@PathVariable String payloadId) {
    PayloadService.PayloadWithRelatedEntities payloadWithRelatedEntities =
        payloadService.findPayloadWithRelatedEntities(payloadId);
    return payloadMapper.toPayloadOutput(
        payloadWithRelatedEntities.payload(),
        payloadWithRelatedEntities.attackPatternIds(),
        payloadWithRelatedEntities.domainIds(),
        payloadWithRelatedEntities.tagIds());
  }

  @PostMapping({PAYLOAD_URI, TENANT_PAYLOAD_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public PayloadOutput createPayload(@Valid @RequestBody PayloadCreateInput input) {
    PayloadCreationService.PayloadInjectorContractCreationResult result =
        this.payloadCreationService.createPayload(input);
    return payloadService.convertPayloadInjectorContractCreationToPayloadOutput(result);
  }

  @PutMapping({PAYLOAD_URI + "/{payloadId}", TENANT_PAYLOAD_URI + "/{payloadId}"})
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public PayloadOutput updatePayload(
      @NotBlank @PathVariable final String payloadId,
      @Valid @RequestBody PayloadUpdateInput input) {
    PayloadCreationService.PayloadInjectorContractCreationResult result =
        this.payloadUpdateService.updatePayload(payloadId, input);
    return payloadService.convertPayloadInjectorContractCreationToPayloadOutput(result);
  }

  @PostMapping({
    PAYLOAD_URI + "/{payloadId}/duplicate",
    TENANT_PAYLOAD_URI + "/{payloadId}/duplicate"
  })
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public PayloadOutput duplicatePayload(@NotBlank @PathVariable final String payloadId) {
    PayloadCreationService.PayloadInjectorContractCreationResult result =
        this.payloadService.duplicate(payloadId);
    return payloadService.convertPayloadInjectorContractCreationToPayloadOutput(result);
  }

  @PostMapping({PAYLOAD_URI + "/upsert", TENANT_PAYLOAD_URI + "/upsert"})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.PAYLOAD)
  @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
  public Payload upsertPayload(@Valid @RequestBody PayloadUpsertInput input) {
    return this.payloadUpsertService.upsertPayload(input);
  }

  @DeleteMapping({PAYLOAD_URI + "/{payloadId}", TENANT_PAYLOAD_URI + "/{payloadId}"})
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PAYLOAD)
  public void deletePayload(@PathVariable String payloadId) {
    payloadRepository
        .findById(payloadId)
        .orElseThrow(() -> new ElementNotFoundException("Payload not found: " + payloadId));
    payloadRepository.deleteById(payloadId);
  }

  @PostMapping({PAYLOAD_URI + "/deprecate", TENANT_PAYLOAD_URI + "/deprecate"})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public void deprecateNonProcessedPayloadsByCollector(
      @Valid @RequestBody PayloadsDeprecateInput input) {
    this.payloadService.deprecateNonProcessedPayloadsByCollector(
        input.collectorId(), input.processedPayloadExternalIds());
  }

  @GetMapping({
    PAYLOAD_URI + "/{payloadId}/documents",
    TENANT_PAYLOAD_URI + "/{payloadId}/documents"
  })
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PAYLOAD)
  @Operation(summary = "Get the Documents used in a payload")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The list of Documents used in a payload")
      })
  public List<RawDocument> documentsFromPayload(@PathVariable String payloadId) {
    return documentService.documentsForPayload(payloadId);
  }

  @GetMapping({
    PAYLOAD_URI + "/{payloadId}/collectors",
    TENANT_PAYLOAD_URI + "/{payloadId}/collectors"
  })
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PAYLOAD)
  @Operation(summary = "Get the Collectors used in a payload remediation")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of Collectors used in a payload remediation")
      })
  public List<Collector> collectorsFromPayload(@PathVariable String payloadId) {
    return collectorsService.collectorsForPayload(payloadId);
  }
}

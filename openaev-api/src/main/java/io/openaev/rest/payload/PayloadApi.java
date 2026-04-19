package io.openaev.rest.payload;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.helper.StreamHelper;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.payload.form.*;
import io.openaev.rest.payload.output.PayloadOutput;
import io.openaev.rest.payload.service.*;
import io.openaev.service.ImportService;
import io.openaev.service.UserService;
import io.openaev.utils.mapper.PayloadMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class PayloadApi extends RestBehavior {

  public static final String PAYLOAD_URI = "/api/payloads";
  public static final String TENANT_PAYLOAD_URI = TENANT_PREFIX + "/payloads";

  private final ImportService importService;
  private final PayloadRepository payloadRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final PayloadService payloadService;
  private final PayloadCreationService payloadCreationService;
  private final PayloadUpdateService payloadUpdateService;
  private final PayloadUpsertService payloadUpsertService;
  private final PayloadExportService payloadExportService;
  private final DocumentService documentService;
  private final CollectorService collectorsService;
  private final UserService userService;
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

  @PostMapping(
      path = {PAYLOAD_URI + "/{payloadId}/export", TENANT_PAYLOAD_URI + "/{payloadId}/export"},
      produces = "application/zip")
  @AccessControl(
      actionPerformed = Action.READ,
      resourceType = ResourceType.PAYLOAD,
      resourceId = "#payloadId")
  public ResponseEntity<byte[]> payloadExport(@NotBlank @PathVariable String payloadId)
      throws IOException {
    List<String> targetIds = List.of(payloadId);
    List<Payload> payloads = StreamHelper.fromIterable(payloadRepository.findAllById(targetIds));
    byte[] zippedExport = payloadExportService.exportPayloadsToZip(payloads);
    String zipName = payloadExportService.getZipFileName();

    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipName);
    headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
    headers.setContentLength(zippedExport.length);

    return new ResponseEntity<>(zippedExport, headers, HttpStatus.OK);
  }

  @PostMapping({PAYLOAD_URI + "/export", TENANT_PAYLOAD_URI + "/export"})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.PAYLOAD)
  public void payloadsExport(
      @RequestBody @Valid final PayloadExportRequestInput payloadExportRequestInput,
      HttpServletResponse response)
      throws IOException {
    List<String> targetIds = payloadExportRequestInput.getTargetsIds();
    User currentUser = userService.currentUser();

    List<Payload> payloads =
        payloadRepository.findAll(
            Specification.<Payload>unrestricted()
                .and(SpecificationUtils.hasIdIn(targetIds))
                .and(
                    SpecificationUtils.hasGrantAccess(
                        currentUser.getId(),
                        currentUser.isAdminOrBypass(),
                        currentUser.getCapabilities().contains(Capability.ACCESS_PAYLOADS),
                        Grant.GRANT_TYPE.OBSERVER)));
    runPayloadExport(payloads, response);
  }

  @PostMapping({PAYLOAD_URI + "/import", TENANT_PAYLOAD_URI + "/import"})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  public void importPayloads(@RequestPart("file") @NotNull MultipartFile file) throws Exception {
    this.importService.handleFileImport(file, null, null);
  }

  private void runPayloadExport(List<Payload> payloads, HttpServletResponse response)
      throws IOException {
    byte[] zippedExport = payloadExportService.exportPayloadsToZip(payloads);
    String zipName = payloadExportService.getZipFileName();

    response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipName);
    response.addHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
    response.setContentLength(zippedExport.length);
    response.setStatus(HttpServletResponse.SC_OK);
    ServletOutputStream outputStream = response.getOutputStream();
    outputStream.write(zippedExport);
    outputStream.flush();
    outputStream.close();
  }

  @DeleteMapping({PAYLOAD_URI + "/{payloadId}", TENANT_PAYLOAD_URI + "/{payloadId}"})
  @AccessControl(
      resourceId = "#payloadId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PAYLOAD)
  public void deletePayload(@PathVariable String payloadId) {
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

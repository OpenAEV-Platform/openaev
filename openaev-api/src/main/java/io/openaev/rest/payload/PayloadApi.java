package io.openaev.rest.payload;

import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawDocument;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.database.specification.SpecificationUtils;
import io.openaev.helper.StreamHelper;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.payload.form.*;
import io.openaev.rest.payload.service.*;
import io.openaev.service.ImportService;
import io.openaev.service.UserService;
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

  private final ImportService importService;
  private final PayloadRepository payloadRepository;
  private final PayloadService payloadService;
  private final PayloadCreationService payloadCreationService;
  private final PayloadUpdateService payloadUpdateService;
  private final PayloadUpsertService payloadUpsertService;
  private final PayloadExportService payloadExportService;
  private final DocumentService documentService;
  private final CollectorService collectorsService;
  private final UserService userService;

  @PostMapping(PAYLOAD_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.PAYLOAD)
  public Page<Payload> payloads(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return this.payloadService.searchPayloads(searchPaginationInput);
  }

  @GetMapping(PAYLOAD_URI + "/{payloadId}")
  @RBAC(
      resourceId = "#payloadId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.PAYLOAD)
  public Payload payload(@PathVariable String payloadId) {
    return payloadRepository.findById(payloadId).orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping(PAYLOAD_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public Payload createPayload(@Valid @RequestBody PayloadCreateInput input) {
    return this.payloadCreationService.createPayload(input);
  }

  @PutMapping(PAYLOAD_URI + "/{payloadId}")
  @RBAC(
      resourceId = "#payloadId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public Payload updatePayload(
      @NotBlank @PathVariable final String payloadId,
      @Valid @RequestBody PayloadUpdateInput input) {
    return this.payloadUpdateService.updatePayload(payloadId, input);
  }

  @PostMapping(PAYLOAD_URI + "/{payloadId}/duplicate")
  @RBAC(
      resourceId = "#payloadId",
      actionPerformed = Action.DUPLICATE,
      resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public Payload duplicatePayload(@NotBlank @PathVariable final String payloadId) {
    return this.payloadService.duplicate(payloadId);
  }

  @PostMapping(PAYLOAD_URI + "/upsert")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.PAYLOAD)
  @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
  public Payload upsertPayload(@Valid @RequestBody PayloadUpsertInput input) {
    return this.payloadUpsertService.upsertPayload(input);
  }

  @PostMapping(path = PAYLOAD_URI + "/{payloadId}/export", produces = "application/zip")
  @RBAC(
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

  @PostMapping(PAYLOAD_URI + "/export")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.PAYLOAD)
  public void payloadsExport(
      @RequestBody @Valid final PayloadExportRequestInput payloadExportRequestInput,
      HttpServletResponse response)
      throws IOException {
    List<String> targetIds = payloadExportRequestInput.getTargetsIds();
    User currentUser = userService.currentUser();

    List<Payload> payloads =
        payloadRepository.findAll(
            Specification.where(SpecificationUtils.<Payload>hasIdIn(targetIds))
                .and(
                    SpecificationUtils.hasGrantAccess(
                        currentUser.getId(),
                        currentUser.isAdminOrBypass(),
                        currentUser.getCapabilities().contains(Capability.ACCESS_PAYLOADS),
                        Grant.GRANT_TYPE.OBSERVER)));
    runPayloadExport(payloads, response);
  }

  @PostMapping(PAYLOAD_URI + "/import")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
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

  @DeleteMapping(PAYLOAD_URI + "/{payloadId}")
  @RBAC(
      resourceId = "#payloadId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.PAYLOAD)
  public void deletePayload(@PathVariable String payloadId) {
    payloadRepository.deleteById(payloadId);
  }

  @PostMapping(PAYLOAD_URI + "/deprecate")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PAYLOAD)
  @Transactional(rollbackOn = Exception.class)
  public void deprecateNonProcessedPayloadsByCollector(
      @Valid @RequestBody PayloadsDeprecateInput input) {
    this.payloadService.deprecateNonProcessedPayloadsByCollector(
        input.collectorId(), input.processedPayloadExternalIds());
  }

  @GetMapping(PAYLOAD_URI + "/{payloadId}/documents")
  @RBAC(
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

  @GetMapping(PAYLOAD_URI + "/{payloadId}/collectors")
  @RBAC(
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

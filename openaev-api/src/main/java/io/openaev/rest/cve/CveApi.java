package io.openaev.rest.cve;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.cve.form.CVEBulkInsertInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.vulnerability.form.*;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.utils.mapper.VulnerabilityMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * @deprecated (since = "1.19.0", forRemoval = true) in favor of @See vulnerabilityApi
 */
@Deprecated(since = "1.19", forRemoval = true)
@RestController
@RequiredArgsConstructor
@Tag(name = "Cve API", description = "Operations related to CVEs")
@RequestMapping({CveApi.CVE_API, CveApi.TENANT_CVE_API})
public class CveApi extends RestBehavior {

  public static final String CVE_API = "/api/cves";
  public static final String TENANT_CVE_API = TENANT_PREFIX + "/cves";

  private final VulnerabilityService vulnerabilityService;
  private final VulnerabilityMapper vulnerabilityMapper;

  @LogExecutionTime
  @Operation(summary = "Search CVEs")
  @PostMapping("/search")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.VULNERABILITY)
  // TxCtx is resolved from the request and applied by the transaction aspect; it scopes the CWEs
  // reached through each vulnerability's association once the cwes table is active.
  public Page<VulnerabilitySimple> searchCves(
      TxCtx ctx, @Valid @RequestBody SearchPaginationInput input) {
    return vulnerabilityService
        .searchVulnerabilities(input)
        .map(vulnerabilityMapper::toVulnerabilitySimple);
  }

  @Operation(summary = "Get a CVE by ID", description = "Fetches detailed CVE info by ID")
  @Transactional
  @GetMapping("/{cveId}")
  @AccessControl(
      resourceId = "#cveId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.VULNERABILITY)
  public VulnerabilityOutput getCve(TxCtx ctx, @PathVariable String cveId) {
    return vulnerabilityMapper.toVulnerabilityOutput(vulnerabilityService.findById(cveId));
  }

  @Operation(
      summary = "Get a CVE by external ID",
      description = "Fetches detailed CVE info by external CVE ID")
  @Transactional
  @GetMapping("/external-id/{externalId}")
  @AccessControl(
      resourceId = "#externalId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.VULNERABILITY)
  public VulnerabilityOutput getCvebyExternalId(TxCtx ctx, @PathVariable String externalId) {
    return vulnerabilityMapper.toVulnerabilityOutput(
        vulnerabilityService.findByExternalId(externalId));
  }

  @Operation(summary = "Create a new CVE")
  @PostMapping
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.VULNERABILITY)
  @Transactional(rollbackFor = Exception.class)
  public VulnerabilitySimple createCve(
      TxCtx ctx, @Valid @RequestBody VulnerabilityCreateInput input) {
    return vulnerabilityMapper.toVulnerabilitySimple(
        vulnerabilityService.createVulnerability(ctx, input));
  }

  @Operation(summary = "Bulk insert CVEs")
  @LogExecutionTime
  @PostMapping("/bulk")
  @Transactional
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.VULNERABILITY)
  public void bulkInsertCVEsForCollector(
      TxCtx ctx, @Valid @RequestBody @NotNull CVEBulkInsertInput input) {
    this.vulnerabilityService.bulkUpsertVulnerabilities(
        ctx, vulnerabilityMapper.fromCVEBulkInsertInput(input));
  }

  @Operation(summary = "Update an existing CVE")
  @PutMapping("/{cveId}")
  @AccessControl(
      resourceId = "#cveId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.VULNERABILITY)
  @Transactional(rollbackFor = Exception.class)
  public VulnerabilitySimple updateCve(
      TxCtx ctx, @PathVariable String cveId, @Valid @RequestBody VulnerabilityUpdateInput input) {
    return vulnerabilityMapper.toVulnerabilitySimple(
        vulnerabilityService.updateVulnerability(ctx, cveId, input));
  }

  @Operation(summary = "Delete a CVE")
  @DeleteMapping("/{cveId}")
  @AccessControl(
      resourceId = "#cveId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.VULNERABILITY)
  @Transactional(rollbackFor = Exception.class)
  public void deleteCve(@PathVariable String cveId) {
    vulnerabilityService.deleteById(cveId);
  }
}

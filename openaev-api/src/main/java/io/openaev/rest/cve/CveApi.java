package io.openaev.rest.cve;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.cve.form.*;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.vulnerability.form.*;
import io.openaev.rest.vulnerability.service.VulnerabilityService;
import io.openaev.utils.mapper.CveMapper;
import io.openaev.utils.mapper.VulnerabilityMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * @deprecated (since = "1.19.0", forRemoval = true) in favor of @See vulnerabilityApi
 */
@Deprecated(since = "1.19", forRemoval = true)
@RestController
@RequiredArgsConstructor
@Tag(name = "Cve API", description = "Operations related to CVEs")
public class CveApi extends RestBehavior {

  public static final String CVE_API = "/api/cves";

  private final VulnerabilityService vulnerabilityService;
  private final VulnerabilityMapper vulnerabilityMapper;
  private final CveMapper cveMapper;

  @LogExecutionTime
  @Operation(summary = "Search CVEs")
  @PostMapping(CVE_API + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.VULNERABILITY)
  public Page<CveSimple> searchCves(@Valid @RequestBody SearchPaginationInput input) {
    return vulnerabilityService.searchVulnerabilities(input).map(cveMapper::toCveSimple);
  }

  @Operation(summary = "Get a CVE by ID", description = "Fetches detailed CVE info by ID")
  @GetMapping(CVE_API + "/{cveId}")
  @RBAC(
      resourceId = "#cveId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.VULNERABILITY)
  public CveOutput getCve(@PathVariable String cveId) {
    return cveMapper.toCveOutput(vulnerabilityService.findById(cveId));
  }

  @Operation(
      summary = "Get a CVE by external ID",
      description = "Fetches detailed CVE info by external CVE ID")
  @GetMapping(CVE_API + "/external-id/{externalId}")
  @RBAC(
      resourceId = "#externalId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.VULNERABILITY)
  public CveOutput getCvebyExternalId(@PathVariable String externalId) {
    return cveMapper.toCveOutput(vulnerabilityService.findByExternalId(externalId));
  }

  @Operation(summary = "Create a new CVE")
  @PostMapping(CVE_API)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.VULNERABILITY)
  @Transactional(rollbackOn = Exception.class)
  public CveSimple createCve(@Valid @RequestBody VulnerabilityCreateInput input) {
    return cveMapper.toCveSimple(vulnerabilityService.createVulnerability(input));
  }

  @Operation(summary = "Bulk insert CVEs")
  @LogExecutionTime
  @PostMapping(CVE_API + "/bulk")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.VULNERABILITY)
  public void bulkInsertCVEsForCollector(@Valid @RequestBody @NotNull CVEBulkInsertInput input) {
    this.vulnerabilityService.bulkUpsertVulnerabilities(
        vulnerabilityMapper.fromCVEBulkInsertInput(input));
  }

  @Operation(summary = "Update an existing CVE")
  @PutMapping(CVE_API + "/{cveId}")
  @RBAC(
      resourceId = "#cveId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.VULNERABILITY)
  @Transactional(rollbackOn = Exception.class)
  public CveSimple updateCve(
      @PathVariable String cveId, @Valid @RequestBody VulnerabilityUpdateInput input) {
    return cveMapper.toCveSimple(vulnerabilityService.updateVulnerability(cveId, input));
  }

  @Operation(summary = "Delete a CVE")
  @DeleteMapping(CVE_API + "/{cveId}")
  @RBAC(
      resourceId = "#cveId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.VULNERABILITY)
  @Transactional(rollbackOn = Exception.class)
  public void deleteCve(@PathVariable String cveId) {
    vulnerabilityService.deleteById(cveId);
  }
}

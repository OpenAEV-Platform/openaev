package io.openaev.rest.cve;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.cve.form.*;
import io.openaev.rest.cve.service.CveService;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.mapper.CveMapper;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "CVE API", description = "Operations related to CVEs")
public class CveApi extends RestBehavior {

  public static final String CVE_API = "/api/cves";

  private final CveService cveService;
  private final CveMapper cveMapper;

  @LogExecutionTime
  @Operation(summary = "Search CVEs")
  @PostMapping(CVE_API + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.CVE)
  public Page<CveSimple> searchCves(@Valid @RequestBody SearchPaginationInput input) {
    return cveService.searchCves(input).map(cveMapper::toCveSimple);
  }

  @Operation(summary = "Get a CVE by ID", description = "Fetches detailed CVE info by ID")
  @GetMapping(CVE_API + "/{cveId}")
  @RBAC(resourceId = "#cveId", actionPerformed = Action.READ, resourceType = ResourceType.CVE)
  public CveOutput getCve(@PathVariable String cveId) {
    return cveMapper.toCveOutput(cveService.findById(cveId));
  }

  @Operation(
      summary = "Get a CVE by external ID",
      description = "Fetches detailed CVE info by external CVE ID")
  @GetMapping(CVE_API + "/external-id/{externalId}")
  @RBAC(resourceId = "#externalId", actionPerformed = Action.READ, resourceType = ResourceType.CVE)
  public CveOutput getCvebyExternalId(@PathVariable String externalId) {
    return cveMapper.toCveOutput(cveService.findByExternalId(externalId));
  }

  @Operation(summary = "Create a new CVE")
  @PostMapping(CVE_API)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.CVE)
  @Transactional(rollbackOn = Exception.class)
  public CveSimple createCve(@Valid @RequestBody CveCreateInput input) {
    return cveMapper.toCveSimple(cveService.createCve(input));
  }

  @Operation(summary = "Bulk insert CVEs")
  @LogExecutionTime
  @PostMapping(CVE_API + "/bulk")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.CVE)
  public void bulkInsertCVEsForCollector(@Valid @RequestBody @NotNull CVEBulkInsertInput input) {
    this.cveService.bulkUpsertCVEs(input);
  }

  @Operation(summary = "Update an existing CVE")
  @PutMapping(CVE_API + "/{cveId}")
  @RBAC(resourceId = "#cveId", actionPerformed = Action.WRITE, resourceType = ResourceType.CVE)
  @Transactional(rollbackOn = Exception.class)
  public CveSimple updateCve(@PathVariable String cveId, @Valid @RequestBody CveUpdateInput input) {
    return cveMapper.toCveSimple(cveService.updateCve(cveId, input));
  }

  @Operation(summary = "Delete a CVE")
  @DeleteMapping(CVE_API + "/{cveId}")
  @RBAC(resourceId = "#cveId", actionPerformed = Action.DELETE, resourceType = ResourceType.CVE)
  @Transactional(rollbackOn = Exception.class)
  public void deleteCve(@PathVariable String cveId) {
    cveService.deleteById(cveId);
  }
}

package io.openaev.api.tenants;

import static io.openaev.api.tenants.TenantMapper.toOutput;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.multitenancy.DependenciesManagerException;
import io.openaev.service.tenants.TenantService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants")
@RequiredArgsConstructor
public class TenantApi {

  private final TenantService tenantService;

  // -- CREATE --

  @Operation(
      summary = "Create a tenant",
      description = "Creates a new tenant (Enterprise edition only)")
  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.TENANT,
      isEnterpriseEdition = true)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TenantOutput create(@Valid @RequestBody TenantInput input)
      throws DependenciesManagerException {
    return toOutput(tenantService.create(TenantMapper.fromInput(null, input)));
  }

  // -- READ --

  @Operation(
      summary = "Get tenant by ID",
      description = "Retrieves a tenant by its unique identifier")
  @AccessControl(
      resourceId = "#tenantId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.TENANT,
      isEnterpriseEdition = true)
  @GetMapping("/{tenantId}")
  public TenantOutput getById(@PathVariable String tenantId) {
    return toOutput(tenantService.findById(tenantId));
  }

  // -- SEARCH --

  @Operation(
      summary = "Search tenants",
      description = "Search tenants with pagination and filtering")
  @AccessControl(
      actionPerformed = Action.READ,
      resourceType = ResourceType.TENANT,
      isEnterpriseEdition = true)
  @PostMapping("/search")
  public Page<TenantOutput> search(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return tenantService.search(searchPaginationInput).map(TenantMapper::toOutput);
  }

  // -- UPDATE --

  @Operation(summary = "Update a tenant", description = "Updates an existing tenant")
  @AccessControl(
      resourceId = "#tenantId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.TENANT,
      isEnterpriseEdition = true)
  @PutMapping("/{tenantId}")
  public TenantOutput update(@PathVariable String tenantId, @Valid @RequestBody TenantInput input) {

    return toOutput(tenantService.update(tenantId, TenantMapper.fromInput(tenantId, input)));
  }

  @Operation(
      summary = "Reactivate a soft-deleted tenant",
      description =
          "Reactivates a previously soft-deleted tenant within the 30-day grace period."
              + " Fails if the grace period has expired.")
  @AccessControl(
      resourceId = "#tenantId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.TENANT,
      isEnterpriseEdition = true)
  @PostMapping("/{tenantId}/reactivate")
  public TenantOutput reactivate(@PathVariable String tenantId) {
    return toOutput(tenantService.reactivate(tenantId));
  }

  // -- DELETE --

  @Operation(
      summary = "Soft-delete a tenant",
      description =
          "Marks a tenant as deleted. Data is preserved for 30 days."
              + " An admin can reactivate the tenant within this grace period."
              + " After 30 days, the tenant and all associated data are permanently removed.")
  @AccessControl(
      resourceId = "#tenantId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.TENANT,
      isEnterpriseEdition = true)
  @DeleteMapping("/{tenantId}")
  public TenantOutput softDelete(@PathVariable String tenantId) {
    return toOutput(tenantService.softDelete(tenantId));
  }
}

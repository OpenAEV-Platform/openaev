package io.openaev.api.ioc_validation;

import static io.openaev.api.ioc_validation.IocValidationMapper.fromSettingsInput;
import static io.openaev.api.ioc_validation.IocValidationMapper.toOutput;
import static io.openaev.api.ioc_validation.IocValidationMapper.toSettingsOutput;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.ioc_validation.dto.IocValidationOutput;
import io.openaev.api.ioc_validation.dto.IocValidationRejectInput;
import io.openaev.api.ioc_validation.dto.IocValidationSettingsInput;
import io.openaev.api.ioc_validation.dto.IocValidationSettingsOutput;
import io.openaev.api.ioc_validation.dto.IocValidationSimpleOutput;
import io.openaev.config.RequireTenantSelector;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.InputValidationException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.UserService;
import io.openaev.service.stix.IocValidationService;
import io.openaev.service.stix.IocValidationSettingsService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenCTI IOC validation requests: review, mandatory approval or rejection, and the tenant safety
 * settings. Reading needs the assessment access capability, deciding needs the assessment launch
 * capability (an approval launches a simulation), and the settings follow the tenant settings
 * capabilities.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({IocValidationApi.IOC_VALIDATION_URI, IocValidationApi.TENANT_IOC_VALIDATION_URI})
@Tag(name = "IOC validations", description = "Validate disseminated OpenCTI indicators")
public class IocValidationApi extends RestBehavior {

  public static final String IOC_VALIDATION_URI = "/api/ioc-validations";
  public static final String TENANT_IOC_VALIDATION_URI = TENANT_PREFIX + "/ioc-validations";

  private final IocValidationService iocValidationService;
  private final IocValidationSettingsService settingsService;
  private final TenantWriteScopeResolver writeScopeResolver;
  private final UserService userService;

  // -- READ --

  @PostMapping("/search")
  @Transactional(readOnly = true)
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.IOC_VALIDATION)
  @Operation(summary = "Search IOC validations")
  public Page<IocValidationSimpleOutput> searchIocValidations(
      TxCtx ctx, @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return iocValidationService
        .search(searchPaginationInput)
        .map(IocValidationMapper::toSimpleOutput);
  }

  @GetMapping("/{iocValidationId}")
  @Transactional(readOnly = true)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#iocValidationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.IOC_VALIDATION)
  @Operation(summary = "Get an IOC validation")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The IOC validation"),
    @ApiResponse(responseCode = "404", description = "IOC validation not found")
  })
  public IocValidationOutput iocValidation(
      TxCtx ctx, @PathVariable @NotBlank final String iocValidationId) {
    return toOutput(iocValidationService.iocValidation(iocValidationId));
  }

  @GetMapping("/settings")
  @Transactional(readOnly = true)
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.TENANT_SETTING)
  @Operation(summary = "Get the IOC validation safety settings")
  public IocValidationSettingsOutput iocValidationSettings(@RequireTenantSelector TxCtx ctx) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return toSettingsOutput(
        settingsService.settings(tenantId),
        iocValidationService.isOpenCtiConfigured(tenantId),
        iocValidationService.isIocValidationConnectorRegistered(tenantId));
  }

  // -- UPDATE --

  @PostMapping("/{iocValidationId}/approve")
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#iocValidationId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.IOC_VALIDATION)
  @Operation(
      summary = "Approve an IOC validation",
      description =
          "Builds the benign validation scenario and launches its simulation. Nothing runs before"
              + " this approval.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The approved IOC validation, now running"),
    @ApiResponse(
        responseCode = "400",
        description = "Not awaiting approval, or nothing can run with the current settings"),
    @ApiResponse(responseCode = "404", description = "IOC validation not found")
  })
  public IocValidationOutput approveIocValidation(
      @RequireTenantSelector TxCtx ctx, @PathVariable @NotBlank final String iocValidationId) {
    return toOutput(iocValidationService.approve(ctx, iocValidationId, userService.currentUser()));
  }

  @PostMapping("/{iocValidationId}/reject")
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(
      resourceId = "#iocValidationId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.IOC_VALIDATION)
  @Operation(
      summary = "Reject an IOC validation",
      description = "Nothing runs; OpenCTI is notified with the optional reason.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The rejected IOC validation"),
    @ApiResponse(responseCode = "400", description = "Not awaiting approval"),
    @ApiResponse(responseCode = "404", description = "IOC validation not found")
  })
  public IocValidationOutput rejectIocValidation(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable @NotBlank final String iocValidationId,
      @RequestBody @Valid final IocValidationRejectInput input) {
    return toOutput(
        iocValidationService.reject(
            ctx, iocValidationId, input.reason(), userService.currentUser()));
  }

  @PutMapping("/settings")
  @Transactional(rollbackFor = Exception.class)
  @LogExecutionTime
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.TENANT_SETTING)
  @Operation(summary = "Update the IOC validation safety settings")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "The stored settings"),
    @ApiResponse(responseCode = "400", description = "A value is unsafe or inconsistent")
  })
  public IocValidationSettingsOutput updateIocValidationSettings(
      @RequireTenantSelector TxCtx ctx, @RequestBody @Valid final IocValidationSettingsInput input)
      throws InputValidationException {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    return toSettingsOutput(
        settingsService.update(tenantId, fromSettingsInput(input)),
        iocValidationService.isOpenCtiConfigured(tenantId),
        iocValidationService.isIocValidationConnectorRegistered(tenantId));
  }
}

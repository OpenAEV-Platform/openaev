package io.openaev.api.notifier;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.UserRoleDescription;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.service.notification.NotifierService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@UserRoleDescription
@RequiredArgsConstructor
@Tag(
    name = "Notifiers management",
    description =
        "Endpoints to manage notifiers - the delivery channels (UI, email, webhook) used by"
            + " notification triggers.")
@Slf4j
public class NotifierApi {

  public static final String NOTIFIER_URI = "/api/notifiers";
  public static final String TENANT_NOTIFIER_URI = TENANT_PREFIX + "/notifiers";

  private final NotifierService notifierService;
  private final NotifierMapper notifierMapper;
  private final PermissionService permissionService;
  private final UserService userService;

  /**
   * NOTIFIER is an open resource for READ/SEARCH so any user can pick notifiers when creating
   * notification triggers, but the configuration payload (webhook URLs, headers, templates...) can
   * hold sensitive values: it is only exposed to users holding the tenant-settings capability that
   * normally grants notifier reads.
   */
  private boolean canSeeConfiguration() {
    return permissionService.hasCapabilityPermission(
        userService.currentUser(), ResourceType.NOTIFIER, Action.READ);
  }

  @LogExecutionTime
  @GetMapping({NOTIFIER_URI, TENANT_NOTIFIER_URI})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.NOTIFIER)
  @Operation(summary = "List notifiers", description = "Get all notifiers of the current tenant")
  @Transactional
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The list of notifiers")})
  public List<NotifierOutput> notifiers() {
    boolean includeConfiguration = canSeeConfiguration();
    return notifierService.findAll().stream()
        .map(notifier -> notifierMapper.toNotifierOutput(notifier, includeConfiguration))
        .toList();
  }

  @LogExecutionTime
  @GetMapping({NOTIFIER_URI + "/{notifierId}", TENANT_NOTIFIER_URI + "/{notifierId}"})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.NOTIFIER)
  @Operation(summary = "Get notifier", description = "Get a notifier by id")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The notifier"),
        @ApiResponse(responseCode = "404", description = "Notifier not found")
      })
  public NotifierOutput notifier(
      @PathVariable @NotBlank @Schema(description = "ID of the notifier") final String notifierId) {
    boolean includeConfiguration = canSeeConfiguration();
    return notifierService
        .findById(notifierId)
        .map(notifier -> notifierMapper.toNotifierOutput(notifier, includeConfiguration))
        .orElseThrow(() -> new ElementNotFoundException("Notifier not found: " + notifierId));
  }

  @LogExecutionTime
  @PostMapping({NOTIFIER_URI + "/search", TENANT_NOTIFIER_URI + "/search"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.NOTIFIER)
  @Operation(summary = "Search notifiers", description = "Search notifiers with pagination")
  @Transactional
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The paginated notifiers")})
  public Page<NotifierOutput> searchNotifiers(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    boolean includeConfiguration = canSeeConfiguration();
    return notifierService
        .search(searchPaginationInput)
        .map(notifier -> notifierMapper.toNotifierOutput(notifier, includeConfiguration));
  }

  @LogExecutionTime
  @PostMapping({NOTIFIER_URI, TENANT_NOTIFIER_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.NOTIFIER)
  @Operation(summary = "Create notifier", description = "Create a notifier")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Notifier created")})
  public NotifierOutput createNotifier(@Valid @RequestBody final NotifierInput input) {
    // caller passed the CREATE capability gate, so the configuration is safe to return
    return notifierMapper.toNotifierOutput(
        notifierService.create(notifierMapper.toNotifier(input)), true);
  }

  @LogExecutionTime
  @PutMapping({NOTIFIER_URI + "/{notifierId}", TENANT_NOTIFIER_URI + "/{notifierId}"})
  @AccessControl(
      resourceId = "#notifierId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.NOTIFIER)
  @Operation(summary = "Update notifier", description = "Update a notifier (built-ins rejected)")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Notifier updated"),
        @ApiResponse(responseCode = "404", description = "Notifier not found")
      })
  public NotifierOutput updateNotifier(
      @PathVariable @NotBlank @Schema(description = "ID of the notifier") final String notifierId,
      @Valid @RequestBody final NotifierInput input) {
    // caller passed the WRITE capability gate, so the configuration is safe to return
    return notifierMapper.toNotifierOutput(
        notifierService.update(notifierId, notifierMapper.toNotifier(input)), true);
  }

  @LogExecutionTime
  @DeleteMapping({NOTIFIER_URI + "/{notifierId}", TENANT_NOTIFIER_URI + "/{notifierId}"})
  @AccessControl(
      resourceId = "#notifierId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.NOTIFIER)
  @Operation(summary = "Delete notifier", description = "Delete a notifier (built-ins rejected)")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Notifier deleted"),
        @ApiResponse(responseCode = "404", description = "Notifier not found")
      })
  public void deleteNotifier(
      @PathVariable @NotBlank @Schema(description = "ID of the notifier") final String notifierId) {
    notifierService.delete(notifierId);
  }

  @LogExecutionTime
  @PostMapping({NOTIFIER_URI + "/{notifierId}/test", TENANT_NOTIFIER_URI + "/{notifierId}/test"})
  @AccessControl(
      resourceId = "#notifierId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.NOTIFIER)
  @Operation(
      summary = "Test notifier",
      description = "Send a sample notification through the notifier to the current user")
  @Transactional
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Test dispatched")})
  public void testNotifier(
      @PathVariable @NotBlank @Schema(description = "ID of the notifier") final String notifierId) {
    notifierService.test(notifierId);
  }
}

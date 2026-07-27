package io.openaev.api.notification_trigger;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.UserRoleDescription;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.notification.NotificationTriggerService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@UserRoleDescription
@RequiredArgsConstructor
@Tag(
    name = "Notification triggers management",
    description =
        "Endpoints to manage notification triggers - live subscriptions to entity events and"
            + " periodic digests, delivered through notifiers.")
@Slf4j
public class NotificationTriggerApi {

  public static final String NOTIFICATION_TRIGGER_URI = "/api/notification-triggers";
  public static final String TENANT_NOTIFICATION_TRIGGER_URI =
      TENANT_PREFIX + "/notification-triggers";

  private final NotificationTriggerService notificationTriggerService;
  private final NotificationTriggerMapper notificationTriggerMapper;

  @LogExecutionTime
  @GetMapping({
    NOTIFICATION_TRIGGER_URI + "/{triggerId}",
    TENANT_NOTIFICATION_TRIGGER_URI + "/{triggerId}"
  })
  // Self-service resource: ownership is enforced in the service (owner only)
  @AccessControl(skipRBAC = true)
  @Operation(summary = "Get notification trigger", description = "Get a notification trigger by id")
  @Transactional
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The notification trigger"),
        @ApiResponse(responseCode = "404", description = "Trigger not found")
      })
  public NotificationTriggerOutput notificationTrigger(
      @PathVariable @NotBlank @Schema(description = "ID of the trigger") final String triggerId) {
    return notificationTriggerService
        .findById(triggerId)
        .map(notificationTriggerMapper::toNotificationTriggerOutput)
        .orElseThrow(
            () -> new ElementNotFoundException("Notification trigger not found: " + triggerId));
  }

  @LogExecutionTime
  @PostMapping({NOTIFICATION_TRIGGER_URI + "/search", TENANT_NOTIFICATION_TRIGGER_URI + "/search"})
  // Self-service resource: results are scoped to the current user in the service
  @AccessControl(skipRBAC = true)
  @Operation(
      summary = "Search notification triggers",
      description = "Search the current user's notification triggers with pagination")
  @Transactional
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The paginated triggers")})
  public Page<NotificationTriggerOutput> searchNotificationTriggers(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return notificationTriggerService
        .search(searchPaginationInput)
        .map(notificationTriggerMapper::toNotificationTriggerOutput);
  }

  @LogExecutionTime
  @PostMapping({NOTIFICATION_TRIGGER_URI, TENANT_NOTIFICATION_TRIGGER_URI})
  // Self-service resource: any authenticated user can create triggers for themselves; targeting
  // other recipients is enforced in the service (admins only)
  @AccessControl(skipRBAC = true)
  @Operation(summary = "Create notification trigger", description = "Create a notification trigger")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Trigger created")})
  public NotificationTriggerOutput createNotificationTrigger(
      @Valid @RequestBody final NotificationTriggerInput input) {
    return notificationTriggerMapper.toNotificationTriggerOutput(
        notificationTriggerService.create(notificationTriggerMapper.toNotificationTrigger(input)));
  }

  @LogExecutionTime
  @PutMapping({
    NOTIFICATION_TRIGGER_URI + "/{triggerId}",
    TENANT_NOTIFICATION_TRIGGER_URI + "/{triggerId}"
  })
  // Self-service resource: ownership is enforced in the service (owner only)
  @AccessControl(skipRBAC = true)
  @Operation(summary = "Update notification trigger", description = "Update a notification trigger")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Trigger updated"),
        @ApiResponse(responseCode = "404", description = "Trigger not found")
      })
  public NotificationTriggerOutput updateNotificationTrigger(
      @PathVariable @NotBlank @Schema(description = "ID of the trigger") final String triggerId,
      @Valid @RequestBody final NotificationTriggerInput input) {
    return notificationTriggerMapper.toNotificationTriggerOutput(
        notificationTriggerService.update(
            triggerId, notificationTriggerMapper.toNotificationTrigger(input)));
  }

  @LogExecutionTime
  @DeleteMapping({
    NOTIFICATION_TRIGGER_URI + "/{triggerId}",
    TENANT_NOTIFICATION_TRIGGER_URI + "/{triggerId}"
  })
  // Self-service resource: ownership is enforced in the service (owner only)
  @AccessControl(skipRBAC = true)
  @Operation(summary = "Delete notification trigger", description = "Delete a notification trigger")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Trigger deleted"),
        @ApiResponse(responseCode = "404", description = "Trigger not found")
      })
  public void deleteNotificationTrigger(
      @PathVariable @NotBlank @Schema(description = "ID of the trigger") final String triggerId) {
    notificationTriggerService.delete(triggerId);
  }
}

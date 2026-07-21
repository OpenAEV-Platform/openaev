package io.openaev.rest.notification;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.UserRoleDescription;
import io.openaev.rest.notification.form.NotificationMapper;
import io.openaev.rest.notification.form.NotificationOutput;
import io.openaev.service.notification.NotificationService;
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
    name = "Notifications",
    description = "Endpoints to access the current user's in-app notifications.")
@Slf4j
public class NotificationApi {

  public static final String NOTIFICATION_URI = "/api/notifications";
  public static final String TENANT_NOTIFICATION_URI = TENANT_PREFIX + "/notifications";

  private final NotificationService notificationService;
  private final NotificationMapper notificationMapper;

  @LogExecutionTime
  @PostMapping({NOTIFICATION_URI + "/me/search", TENANT_NOTIFICATION_URI + "/me/search"})
  // Self-service resource: results are scoped to the current user in the service
  @AccessControl(skipRBAC = true)
  @Operation(
      summary = "Search my notifications",
      description = "Search the current user's notifications with pagination")
  @Transactional
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "The paginated notifications")})
  public Page<NotificationOutput> searchMyNotifications(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return notificationService
        .searchMyNotifications(searchPaginationInput)
        .map(notificationMapper::toNotificationOutput);
  }

  @LogExecutionTime
  @GetMapping({NOTIFICATION_URI + "/me/unread-count", TENANT_NOTIFICATION_URI + "/me/unread-count"})
  // Self-service resource: scoped to the current user
  @AccessControl(skipRBAC = true)
  @Operation(
      summary = "My unread notifications count",
      description = "Number of unread notifications for the current user")
  @Transactional
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The unread count")})
  public long unreadNotificationsCount() {
    return notificationService.unreadCount();
  }

  @LogExecutionTime
  @PutMapping({
    NOTIFICATION_URI + "/{notificationId}/read",
    TENANT_NOTIFICATION_URI + "/{notificationId}/read"
  })
  // Self-service resource: ownership is enforced in the service
  @AccessControl(skipRBAC = true)
  @Operation(
      summary = "Mark notification read/unread",
      description = "Set the read flag of one of the current user's notifications")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Notification updated"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
      })
  public NotificationOutput markNotificationRead(
      @PathVariable @NotBlank @Schema(description = "ID of the notification")
          final String notificationId,
      @RequestParam(name = "read", defaultValue = "true") final boolean read) {
    return notificationMapper.toNotificationOutput(
        notificationService.markRead(notificationId, read));
  }

  @LogExecutionTime
  @PutMapping({NOTIFICATION_URI + "/me/read-all", TENANT_NOTIFICATION_URI + "/me/read-all"})
  // Self-service resource: scoped to the current user
  @AccessControl(skipRBAC = true)
  @Operation(
      summary = "Mark all notifications read",
      description = "Mark all of the current user's notifications as read")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Notifications updated")})
  public void markAllNotificationsRead() {
    notificationService.markAllRead();
  }

  @LogExecutionTime
  @DeleteMapping({
    NOTIFICATION_URI + "/{notificationId}",
    TENANT_NOTIFICATION_URI + "/{notificationId}"
  })
  // Self-service resource: ownership is enforced in the service
  @AccessControl(skipRBAC = true)
  @Operation(
      summary = "Delete notification",
      description = "Delete one of the current user's notifications")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Notification deleted"),
        @ApiResponse(responseCode = "404", description = "Notification not found")
      })
  public void deleteNotification(
      @PathVariable @NotBlank @Schema(description = "ID of the notification")
          final String notificationId) {
    notificationService.delete(notificationId);
  }
}

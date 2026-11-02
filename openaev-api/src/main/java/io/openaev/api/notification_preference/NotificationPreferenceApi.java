package io.openaev.api.notification_preference;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.annotation.NoTenantScope;
import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.UserRoleDescription;
import io.openaev.service.notification.NotificationPreferenceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * The current user's notification preferences: which channels (in-app, email) each notification
 * type is received on. Global to the user, not per tenant.
 */
@RestController
@UserRoleDescription
// Preferences belong to the user, not to a tenant (like the language or the theme).
@NoTenantScope
@RequiredArgsConstructor
@Tag(
    name = "Notification preferences",
    description = "Endpoints to read and change the current user's notification preferences.")
public class NotificationPreferenceApi {

  public static final String NOTIFICATION_PREFERENCE_URI = "/api/notification-preferences";
  public static final String TENANT_NOTIFICATION_PREFERENCE_URI =
      TENANT_PREFIX + "/notification-preferences";

  private final NotificationPreferenceService notificationPreferenceService;

  @LogExecutionTime
  @GetMapping({NOTIFICATION_PREFERENCE_URI + "/me", TENANT_NOTIFICATION_PREFERENCE_URI + "/me"})
  // Self-service resource: always the current user's own preferences
  @AccessControl(skipRBAC = true)
  @Operation(
      summary = "My notification preferences",
      description =
          "Every notification type with its channels; a type never changed has the defaults (in-app"
              + " on, email off).")
  @Transactional(readOnly = true)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The preferences")})
  public List<NotificationPreferenceOutput> myPreferences() {
    return notificationPreferenceService.myPreferences();
  }

  @LogExecutionTime
  @PutMapping({NOTIFICATION_PREFERENCE_URI + "/me", TENANT_NOTIFICATION_PREFERENCE_URI + "/me"})
  // Self-service resource: always the current user's own preferences
  @AccessControl(skipRBAC = true)
  @Operation(
      summary = "Change my notification preferences",
      description = "Sets the channels of the given types; the other types are left unchanged.")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(
      value = {@ApiResponse(responseCode = "200", description = "All the preferences, updated")})
  public List<NotificationPreferenceOutput> updateMyPreferences(
      @RequestBody @Valid List<@Valid NotificationPreferenceInput> input) {
    return notificationPreferenceService.updateMyPreferences(input);
  }
}

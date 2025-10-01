package io.openaev.rest.notification_rule;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.aop.UserRoleDescription;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.notification_rule.form.CreateNotificationRuleInput;
import io.openaev.rest.notification_rule.form.NotificationRuleMapper;
import io.openaev.rest.notification_rule.form.NotificationRuleOutput;
import io.openaev.rest.notification_rule.form.UpdateNotificationRuleInput;
import io.openaev.service.NotificationRuleService;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@UserRoleDescription
@Tag(
    name = "Notification rules management",
    description =
        "Endpoints to manage NotificationRules. NotificationRules are used to subscribe to notifications. As of today, only scenario degradation score is supported.")
@Slf4j
public class NotificationRuleApi {

  public static final String NOTIFICATION_RULE_URI = "/api/notification-rules";

  private final NotificationRuleService notificationRuleService;
  private final UserService userService;
  private final NotificationRuleMapper notificationRuleMapper;

  public NotificationRuleApi(
      NotificationRuleService notificationRuleService,
      NotificationRuleMapper notificationRuleMapper,
      UserService userService) {
    super();
    this.notificationRuleService = notificationRuleService;
    this.notificationRuleMapper = notificationRuleMapper;
    this.userService = userService;
  }

  @LogExecutionTime
  @GetMapping(NOTIFICATION_RULE_URI + "/{notificationRuleId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.NOTIFICATION_RULE)
  @Operation(description = "Get NotificationRule by Id", summary = "Get NotificationRule")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The NotificationRule")})
  public NotificationRuleOutput findNotificationRule(
      @PathVariable @NotBlank @Schema(description = "ID of the notification rule")
          final String notificationRuleId) {
    return notificationRuleService
        .findById(notificationRuleId)
        .map(notificationRuleMapper::toNotificationRuleOutput)
        .orElse(null);
  }

  @LogExecutionTime
  @GetMapping(NOTIFICATION_RULE_URI + "/resource/{resourceId}")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.NOTIFICATION_RULE)
  @Operation(
      description = "Get NotificationRule by resource id for the current user",
      summary = "Get NotificationRule by resource id")
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "The NotificationRule")})
  public List<NotificationRuleOutput> findNotificationRuleByResource(
      @PathVariable
          @NotBlank
          @Schema(description = "Resource id of the resource associated with the notification rule")
          final String resourceId) {
    return notificationRuleService
        .findNotificationRuleByResourceAndUser(resourceId, userService.currentUser().getId())
        .stream()
        .map(notificationRuleMapper::toNotificationRuleOutput)
        .collect(Collectors.toList());
  }

  @LogExecutionTime
  @GetMapping(NOTIFICATION_RULE_URI)
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.NOTIFICATION_RULE)
  @Operation(description = "Get All NotificationRules", summary = "Get NotificationRules")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The list of all NotificationRules")
      })
  public List<NotificationRuleOutput> notificationRules() {
    return notificationRuleService.findAll().stream()
        .map(notificationRuleMapper::toNotificationRuleOutput)
        .toList();
  }

  @LogExecutionTime
  @DeleteMapping(NOTIFICATION_RULE_URI + "/{notificationRuleId}")
  @RBAC(
      resourceId = "#notificationRuleId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.NOTIFICATION_RULE)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Delete NotificationRule", description = "NotificationRule needs to exists")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "NotificationRule deleted"),
        @ApiResponse(responseCode = "404", description = "NotificationRule not found")
      })
  public void deleteNotificationRule(
      @PathVariable @NotBlank @Schema(description = "ID of the notification rule")
          final String notificationRuleId) {
    notificationRuleService.deleteNotificationRule(notificationRuleId);
  }

  @LogExecutionTime
  @PostMapping(NOTIFICATION_RULE_URI)
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.NOTIFICATION_RULE)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Create NotificationRule", description = "Create a NotificationRule")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "NotificationRule created"),
        @ApiResponse(responseCode = "404", description = "Resource not found")
      })
  public NotificationRuleOutput createNotificationRule(
      @Valid @RequestBody final CreateNotificationRuleInput input) {
    return notificationRuleMapper.toNotificationRuleOutput(
        notificationRuleService.createNotificationRule(
            notificationRuleMapper.toNotificationRule(input)));
  }

  @LogExecutionTime
  @PutMapping(NOTIFICATION_RULE_URI + "/{notificationRuleId}")
  @RBAC(
      resourceId = "#notificationRuleId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.NOTIFICATION_RULE)
  @Transactional(rollbackFor = Exception.class)
  @Operation(summary = "Update NotificationRule", description = "Update a NotificationRule")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "NotificationRule updated"),
        @ApiResponse(responseCode = "404", description = "NotificationRule not found")
      })
  public NotificationRuleOutput updateNotificationRule(
      @PathVariable @NotBlank @Schema(description = "ID of the notification rule")
          final String notificationRuleId,
      @Valid @RequestBody final UpdateNotificationRuleInput input) {
    return notificationRuleMapper.toNotificationRuleOutput(
        notificationRuleService.updateNotificationRule(notificationRuleId, input.getSubject()));
  }

  @LogExecutionTime
  @PostMapping(NOTIFICATION_RULE_URI + "/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.NOTIFICATION_RULE)
  @Operation(
      description = "Search NotificationRules corresponding to search criteria",
      summary = "Search NotificationRules")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "The list of all NotificationRules corresponding to the search criteria")
      })
  public Page<NotificationRuleOutput> searchNotificationRules(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return notificationRuleService
        .searchNotificationRule(searchPaginationInput)
        .map(notificationRuleMapper::toNotificationRuleOutput);
  }
}

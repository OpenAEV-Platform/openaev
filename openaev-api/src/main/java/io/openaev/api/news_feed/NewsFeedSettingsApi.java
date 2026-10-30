package io.openaev.api.news_feed;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.UserRoleDescription;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.NewsFeedItemService;
import io.openaev.xtmhub.XtmHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@UserRoleDescription
@RequiredArgsConstructor
@Tag(
    name = "News feed settings",
    description = "Endpoints to manage the current user's XTM Hub news feed preferences.")
public class NewsFeedSettingsApi {

  public static final String TENANT_NEWS_FEED_SETTINGS_URI = TENANT_PREFIX + "/news-feed-settings";

  private final NewsFeedItemService newsFeedItemService;
  private final XtmHubService xtmHubService;

  // -- READ --

  @LogExecutionTime
  @GetMapping(TENANT_NEWS_FEED_SETTINGS_URI + "/me")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.NEWS_FEED)
  @Operation(
      summary = "Get my news feed settings",
      description =
          "Returns the news feed types the current user is subscribed to, and whether the news"
              + " feed is served by XTM Hub. The endpoint is not found when the XTM_HUB_NEWS_FEED"
              + " feature is disabled.")
  @Transactional(readOnly = true)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The news feed settings of the user"),
        @ApiResponse(responseCode = "404", description = "The news feed feature is disabled")
      })
  public NewsFeedSettingsOutput getMyNewsFeedSettings(TxCtx ctx) {
    assertFeatureEnabled();
    return new NewsFeedSettingsOutput(
        newsFeedItemService.getMySubscribedTypes(), xtmHubService.isNewsFeedAvailable(ctx));
  }

  // -- UPDATE --

  @LogExecutionTime
  @PutMapping(TENANT_NEWS_FEED_SETTINGS_URI + "/me")
  // Self-service: a user only ever changes their own preferences, whatever their capabilities.
  // Adding actionPerformed allows this endpoint to be audit logged.
  @AccessControl(skipRBAC = true, actionPerformed = Action.WRITE, resourceType = ResourceType.USER)
  @Operation(
      summary = "Update my news feed settings",
      description =
          "Subscribes the current user to the given news feed types and only those. Only allowed"
              + " when a tenant in scope is registered on XTM Hub, and not found when the"
              + " XTM_HUB_NEWS_FEED feature is disabled.")
  @Transactional(rollbackFor = Exception.class)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The updated news feed settings"),
        @ApiResponse(responseCode = "403", description = "The tenant is not registered on XTM Hub"),
        @ApiResponse(responseCode = "404", description = "The news feed feature is disabled")
      })
  public NewsFeedSettingsOutput updateMyNewsFeedSettings(
      TxCtx ctx, @RequestBody @Valid NewsFeedSettingsInput input) {
    assertFeatureEnabled();
    return new NewsFeedSettingsOutput(
        xtmHubService.updateMyNewsFeedSubscribedTypes(ctx, input.subscribedTypes()), true);
  }

  private void assertFeatureEnabled() {
    if (!newsFeedItemService.isFeatureEnabled()) {
      throw new ElementNotFoundException("The news feed feature is disabled");
    }
  }
}

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
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.xtmhub.XtmHubService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@UserRoleDescription
@RequiredArgsConstructor
@Tag(
    name = "News feed",
    description = "Endpoints to access the current user's XTM Hub news feed items.")
public class NewsFeedItemApi {

  public static final String TENANT_NEWS_FEED_URI = TENANT_PREFIX + "/news-feed";

  private final NewsFeedItemService newsFeedItemService;
  private final XtmHubService xtmHubService;

  @LogExecutionTime
  @PostMapping(TENANT_NEWS_FEED_URI + "/me/search")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.NEWS_FEED)
  @Operation(
      summary = "Search my news feed items",
      description =
          "Search the current user's XTM Hub news feed items with pagination. The page is empty"
              + " when the tenant is not registered on XTM Hub, and the endpoint is not found"
              + " when the XTM_HUB_NEWS_FEED feature is disabled.")
  @Transactional(readOnly = true)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The paginated news feed items"),
        @ApiResponse(responseCode = "404", description = "The news feed feature is disabled")
      })
  public Page<NewsFeedItemOutput> searchMyNewsFeedItems(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    assertFeatureEnabled();
    return xtmHubService
        .searchMyNewsFeedItems(ctx, searchPaginationInput)
        .map(NewsFeedItemMapper::toOutput);
  }

  @LogExecutionTime
  @GetMapping(TENANT_NEWS_FEED_URI + "/me/unread-count")
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.NEWS_FEED)
  @Operation(
      summary = "Count my unread news feed items",
      description = "The unread items of the current user, 0 when the tenant is not on XTM Hub.")
  @Transactional(readOnly = true)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The number of unread items"),
        @ApiResponse(responseCode = "404", description = "The news feed feature is disabled")
      })
  public long countMyUnreadNewsFeedItems(TxCtx ctx) {
    assertFeatureEnabled();
    return xtmHubService.countMyUnreadNewsFeedItems(ctx);
  }

  @LogExecutionTime
  @PutMapping(TENANT_NEWS_FEED_URI + "/me/read-all")
  // Self-service: a user only ever marks their own items as read, whatever their capabilities.
  // Adding actionPerformed allows this endpoint to be audit logged.
  @AccessControl(skipRBAC = true, actionPerformed = Action.WRITE, resourceType = ResourceType.USER)
  @Operation(
      summary = "Mark all my news feed items as read",
      description =
          "Marks the unread items of the current user as read, none when the tenant is not on XTM Hub.")
  @Transactional(rollbackFor = Exception.class)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "The items are read"),
        @ApiResponse(responseCode = "404", description = "The news feed feature is disabled")
      })
  public void markAllMyNewsFeedItemsRead(TxCtx ctx) {
    assertFeatureEnabled();
    xtmHubService.markMyNewsFeedItemsRead(ctx);
  }

  private void assertFeatureEnabled() {
    if (!newsFeedItemService.isFeatureEnabled()) {
      throw new ElementNotFoundException("The news feed feature is disabled");
    }
  }
}

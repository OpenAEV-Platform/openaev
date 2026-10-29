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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@UserRoleDescription
@RequiredArgsConstructor
@Tag(
    name = "News feed",
    description = "Endpoints to access the current user's XTM Hub news feed items.")
public class NewsFeedItemApi {

  public static final String NEWS_FEED_URI = "/api/news-feed";
  public static final String TENANT_NEWS_FEED_URI = TENANT_PREFIX + "/news-feed";

  private final NewsFeedItemService newsFeedItemService;
  private final XtmHubService xtmHubService;

  @LogExecutionTime
  @PostMapping({NEWS_FEED_URI + "/me/search", TENANT_NEWS_FEED_URI + "/me/search"})
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.NEWS_FEED)
  @Operation(
      summary = "Search my news feed items",
      description =
          "Search the current user's XTM Hub news feed items with pagination. The page is empty"
              + " when no tenant in scope is registered on XTM Hub, and the endpoint is not found"
              + " when the XTM_HUB_NEWS_FEED feature is disabled.")
  @Transactional(readOnly = true)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The paginated news feed items"),
        @ApiResponse(responseCode = "404", description = "The news feed feature is disabled")
      })
  public Page<NewsFeedItemOutput> searchMyNewsFeedItems(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    if (!newsFeedItemService.isFeatureEnabled()) {
      throw new ElementNotFoundException("The news feed feature is disabled");
    }
    return xtmHubService
        .searchMyNewsFeedItems(ctx, searchPaginationInput)
        .map(NewsFeedItemMapper::toOutput);
  }
}

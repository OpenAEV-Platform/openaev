package io.openaev.api.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * Input of a bulk processing call (delete, mark read/unread) for the current user's notifications.
 * Mirrors the platform-wide bulk convention: either an explicit id list or a search input
 * (select-all with optional exclusions).
 */
@Setter
@Getter
public class NotificationBulkProcessingInput {

  /**
   * The search input used to select the notifications to process (select all). Must be provided if
   * notificationIdsToProcess is not provided.
   */
  @JsonProperty("search_pagination_input")
  @Schema(description = "Search input selecting the notifications to process (select all)")
  private SearchPaginationInput searchPaginationInput;

  /**
   * The list of notifications to process. Must be provided if searchPaginationInput is not
   * provided.
   */
  @JsonProperty("notification_ids_to_process")
  @Schema(description = "Explicit ids of the notifications to process")
  private List<String> notificationIdsToProcess;

  /** The list of notifications to ignore from the search input. */
  @JsonProperty("notification_ids_to_ignore")
  @Schema(description = "Ids excluded from the select-all scope")
  private List<String> notificationIdsToIgnore;
}

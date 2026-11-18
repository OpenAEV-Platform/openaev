package io.openaev.api.news_feed;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.NewsFeedType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record NewsFeedSettingsInput(
    @JsonProperty("news_feed_settings_subscribed_types")
        @Schema(
            description =
                "The news feed types the current user wants to receive, and only those. An empty"
                    + " list unsubscribes the user from everything")
        @NotNull
        @Size(max = MAX_SUBSCRIBED_TYPES)
        List<@NotNull NewsFeedType> subscribedTypes) {

  /** Upper bound of the payload, well above the number of news feed types. */
  public static final int MAX_SUBSCRIBED_TYPES = 50;
}

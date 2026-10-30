package io.openaev.api.news_feed;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.NewsFeedType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record NewsFeedSettingsInput(
    @JsonProperty("news_feed_settings_subscribed_types")
        @Schema(
            description =
                "The news feed types the current user wants to receive, and only those. An empty"
                    + " list unsubscribes the user from everything")
        @NotNull
        List<NewsFeedType> subscribedTypes) {}

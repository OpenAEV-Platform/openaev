package io.openaev.api.news_feed;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.NewsFeedType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record NewsFeedSettingsOutput(
    @JsonProperty("news_feed_settings_subscribed_types")
        @Schema(description = "The news feed types the current user is subscribed to")
        @NotNull
        List<NewsFeedType> subscribedTypes,
    @JsonProperty("news_feed_settings_hub_registered")
        @Schema(
            description =
                "Whether the news feed is served by XTM Hub, that is a tenant in scope is"
                    + " registered. The preferences can only be changed when it is",
            requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hubRegistered) {}

package io.openaev.api.news_feed;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.NewsFeedType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;

public record NewsFeedItemOutput(
    @JsonProperty("news_feed_item_id") @Schema(description = "ID of the news feed item") @NotBlank
        String id,
    @JsonProperty("news_feed_item_hub_id") @Schema(description = "ID of the item in XTM Hub")
        String hubItemId,
    @JsonProperty("news_feed_item_title") @Schema(description = "Title of the item") String title,
    @JsonProperty("news_feed_item_type")
        @Schema(description = "Type of the item, absent when OpenAEV no longer knows its type")
        NewsFeedType type,
    @JsonProperty("news_feed_item_tags") @Schema(description = "Tags of the item")
        List<String> tags,
    @JsonProperty("news_feed_item_url_path")
        @Schema(description = "Path of the item in XTM Hub, to append to the XTM Hub URL")
        String urlPath,
    @JsonProperty("news_feed_item_creation_date") @Schema(description = "Creation date of the item")
        Instant creationDate,
    @JsonProperty("news_feed_item_is_read") @Schema(description = "Whether the item was read")
        boolean read) {}

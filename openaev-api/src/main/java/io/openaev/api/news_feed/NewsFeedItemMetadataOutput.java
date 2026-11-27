package io.openaev.api.news_feed;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NewsFeedItemMetadataOutput(
    @JsonProperty("news_feed_item_metadata_key")
        @Schema(description = "Key of the detail, as XTM Hub sends it")
        @NotBlank
        String key,
    @JsonProperty("news_feed_item_metadata_value")
        @Schema(description = "Value of the detail, as XTM Hub sends it")
        @NotNull
        String value) {}

package io.openaev.xtmone.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record ChatbotConfigOutput(
    @JsonProperty("xtm_one_url") String xtmOneUrl,
    @JsonProperty("xtm_one_configured") boolean xtmOneConfigured) {}

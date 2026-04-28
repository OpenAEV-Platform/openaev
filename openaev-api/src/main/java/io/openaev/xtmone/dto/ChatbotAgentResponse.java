package io.openaev.xtmone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatbotAgentResponse(
    @JsonProperty("content") String content,
    @JsonProperty("status") String status,
    @JsonProperty("error") String error,
    @JsonProperty("code") Integer code) {}

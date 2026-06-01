package io.openaev.api.xtmone.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response body for the synchronous XTM One agent proxy endpoints. The {@code status} is either
 * {@code "success"} or {@code "error"}; {@code error} is only populated on the error variants.
 *
 * <p>Use the {@link #success(String)} / {@link #error(String)} static factories to keep the shape
 * stable across all controllers that surface XTM One agent responses.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentCallOutput(
    @JsonProperty("content") String content,
    @JsonProperty("status") String status,
    @JsonProperty("error") String error) {

  public static AgentCallOutput success(String content) {
    return new AgentCallOutput(content, "success", null);
  }

  public static AgentCallOutput error(String message) {
    return new AgentCallOutput("", "error", message);
  }
}

package io.openaev.rest.stream.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class AiResult {

  @JsonProperty("chunk_id")
  private String id;

  @JsonProperty("chunk_content")
  private String content;

  public AiResult(String id, String content) {
    this.id = id;
    this.content = content;
  }
}

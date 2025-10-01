package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PayloadCommandBlock {

  @JsonProperty("command_executor")
  private String executor;

  @JsonProperty("command_content")
  private String content;

  @JsonProperty("payload_cleanup_command")
  private List<String> cleanupCommand;

  public PayloadCommandBlock() {}

  public PayloadCommandBlock(String executor, String content, List<String> cleanupCommand) {
    this.executor = executor;
    this.content = content;
    this.cleanupCommand = cleanupCommand;
  }
}

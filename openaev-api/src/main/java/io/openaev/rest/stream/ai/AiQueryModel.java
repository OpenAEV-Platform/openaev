package io.openaev.rest.stream.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Getter;

@Getter
public class AiQueryModel {

  @JsonProperty("model")
  private String model;

  @JsonProperty("stream")
  private Boolean stream;

  @JsonProperty("messages")
  private List<AiQueryMessageModel> messages;

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Boolean getStream() {
    return stream;
  }

  public void setStream(Boolean stream) {
    this.stream = stream;
  }

  public List<AiQueryMessageModel> getMessages() {
    return messages;
  }

  public void setMessages(List<AiQueryMessageModel> messages) {
    this.messages = messages;
  }
}

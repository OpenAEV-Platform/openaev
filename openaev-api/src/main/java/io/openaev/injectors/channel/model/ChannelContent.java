package io.openaev.injectors.channel.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.injectors.email.model.EmailContent;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ChannelContent extends EmailContent {

  @JsonProperty("articles")
  private List<String> articles = new ArrayList<>();

  @JsonProperty("emailing")
  private boolean emailing;
}

package io.openaev.api.chaining.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OutputTypeDescriptorOutput {

  @JsonProperty("type")
  private String type;

  @JsonProperty("label")
  private String label;

  @JsonProperty("fields")
  private List<FieldDescriptor> fields;

  @Getter
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class FieldDescriptor {

    @JsonProperty("key")
    private String key;

    @JsonProperty("label")
    private String label;

    @JsonProperty("type")
    private String type;
  }
}

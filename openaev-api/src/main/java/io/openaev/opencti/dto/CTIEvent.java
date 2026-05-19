package io.openaev.opencti.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.springframework.validation.annotation.Validated;

@Getter
@Validated
public class CTIEvent {
  @Getter
  public static class Internal {
    @JsonProperty(value = "work_id", required = true)
    @NotBlank
    private String workId;
  }

  @Getter
  public static class Event {
    @JsonProperty(value = "stix_objects", required = true)
    @NotBlank
    private String stixObjects;
  }

  @JsonProperty(value = "internal", required = true)
  @NotNull
  private Internal internal;

  @JsonProperty(value = "event", required = true)
  @NotNull
  private Event event;
}

package io.openaev.opencti.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // Id of the OpenCTI draft the enriched object lives in; null or blank in the live graph.
    @JsonProperty(value = "draft_id")
    private String draftId;

    // What triggered the enrichment in OpenCTI: "create" or "update" (null when manual).
    @JsonProperty(value = "trigger")
    private String trigger;

    /** True when the enrichment comes from an unvalidated OpenCTI draft. */
    @JsonIgnore
    public boolean isInDraft() {
      return draftId != null && !draftId.isBlank();
    }
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

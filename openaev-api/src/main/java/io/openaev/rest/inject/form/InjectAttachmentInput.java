package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InjectAttachmentInput {

  @NotBlank
  @JsonProperty("attachment_id")
  private String attachmentId;

  @NotBlank
  @JsonProperty("authorisation")
  private String authorisation;
}

package io.openaev.rest.document.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class FolderInput {

  @JsonProperty("folder_name")
  @NotBlank
  private String name;

  // Optional parent folder id (null = root).
  @JsonProperty("folder_parent")
  private String parentId;
}

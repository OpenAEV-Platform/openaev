package io.openaev.rest.document.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DocumentCreateInput {

  @JsonProperty("document_description")
  private String description;

  @JsonProperty("document_tags")
  private List<String> tagIds = new ArrayList<>();

  @JsonProperty("document_exercises")
  private List<String> exerciseIds = new ArrayList<>();

  @JsonProperty("document_scenarios")
  private List<String> scenarioIds = new ArrayList<>();

  // Optional folder to organize the file into (null = tenant root).
  @JsonProperty("document_folder")
  private String folderId;

  // "DOCUMENT" (default) or "MALWARE_SAMPLE"; a malware sample is stored as an encrypted zip.
  @JsonProperty("document_kind")
  private String kind;
}

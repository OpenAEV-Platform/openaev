package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FindingCommentInput {

  // 4000 chars mirrors the FindingComment entity / DB CHECK constraint (see
  // V6_20260730140000000__Add_finding_comments for rationale). No frontend character-counter yet
  // -- TODO: add a soft counter at 4000 in the "Write a comment" create form once built.
  @JsonProperty("finding_comment_content")
  @NotBlank
  @Size(max = 4000)
  private String content;
}

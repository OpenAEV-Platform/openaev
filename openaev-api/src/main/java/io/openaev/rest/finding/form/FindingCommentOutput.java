package io.openaev.rest.finding.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.FindingComment;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

/**
 * Read-facing view of a {@link FindingComment}: same field set as the entity, but resolves {@code
 * author} to a display name instead of leaking the JPA entity (and its lazy associations)
 * straight through the controller. Mirrors the {@code user_*} naming convention used by {@code
 * PlayerOutput}.
 */
@Builder
@Getter
public class FindingCommentOutput {

  @JsonProperty("finding_comment_id")
  private String id;

  @JsonProperty("finding_comment_finding_id")
  private String findingId;

  @JsonProperty("finding_comment_author_id")
  private String authorId;

  @JsonProperty("finding_comment_author_firstname")
  private String authorFirstname;

  @JsonProperty("finding_comment_author_lastname")
  private String authorLastname;

  @JsonProperty("finding_comment_content")
  private String content;

  @JsonProperty("finding_comment_created_at")
  private Instant creationDate;

  @JsonProperty("finding_comment_updated_at")
  private Instant updateDate;

  public static FindingCommentOutput from(FindingComment comment) {
    return FindingCommentOutput.builder()
        .id(comment.getId())
        .findingId(comment.getFinding().getId())
        .authorId(comment.getAuthor().getId())
        .authorFirstname(comment.getAuthor().getFirstname())
        .authorLastname(comment.getAuthor().getLastname())
        .content(comment.getContent())
        .creationDate(comment.getCreationDate())
        .updateDate(comment.getUpdateDate())
        .build();
  }
}

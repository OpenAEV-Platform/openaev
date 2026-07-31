package io.openaev.rest.finding;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.finding.form.FindingCommentInput;
import io.openaev.rest.finding.form.FindingCommentOutput;
import io.openaev.rest.helper.RestBehavior;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class FindingCommentApi extends RestBehavior {

  public static final String FINDING_COMMENTS_URI = "/api/findings/{id}/comments";
  public static final String TENANT_FINDING_COMMENTS_URI =
      TENANT_PREFIX + "/findings/{id}/comments";
  public static final String FINDING_COMMENT_URI = "/api/findings/comments/{commentId}";
  public static final String TENANT_FINDING_COMMENT_URI =
      TENANT_PREFIX + "/findings/comments/{commentId}";

  private final FindingCommentService findingCommentService;

  // LIST/CREATE are gated directly against the parent Finding: the finding id is already known
  // from the path, so there is no need for the FINDING_COMMENT parent-permission indirection used
  // below for update/delete (which only have a comment id).
  @GetMapping({FINDING_COMMENTS_URI, TENANT_FINDING_COMMENTS_URI})
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.READ,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<List<FindingCommentOutput>> findingComments(
      @PathVariable @NotNull final String id) {
    return ResponseEntity.ok(
        findingCommentService.findByFinding(id).stream().map(FindingCommentOutput::from).toList());
  }

  @PostMapping({FINDING_COMMENTS_URI, TENANT_FINDING_COMMENTS_URI})
  @Transactional
  @AccessControl(
      resourceId = "#id",
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.FINDING)
  public ResponseEntity<FindingCommentOutput> createFindingComment(
      @PathVariable @NotNull final String id,
      @RequestBody @Valid @NotNull final FindingCommentInput input) {
    return ResponseEntity.ok(
        FindingCommentOutput.from(findingCommentService.createComment(id, input.getContent())));
  }

  // UPDATE/DELETE only carry a commentId - permission resolves via ResourceType.FINDING_COMMENT
  // -> PermissionService#resolveTarget() -> parent Finding, mirroring INJECT/OBJECTIVE/EVALUATION.
  @PutMapping({FINDING_COMMENT_URI, TENANT_FINDING_COMMENT_URI})
  @Transactional
  @AccessControl(
      resourceId = "#commentId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.FINDING_COMMENT)
  public ResponseEntity<FindingCommentOutput> updateFindingComment(
      @PathVariable @NotNull final String commentId,
      @RequestBody @Valid @NotNull final FindingCommentInput input) {
    return ResponseEntity.ok(
        FindingCommentOutput.from(
            findingCommentService.updateComment(commentId, input.getContent())));
  }

  @DeleteMapping({FINDING_COMMENT_URI, TENANT_FINDING_COMMENT_URI})
  @Transactional
  @AccessControl(
      resourceId = "#commentId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.FINDING_COMMENT)
  public ResponseEntity<FindingCommentOutput> deleteFindingComment(
      @PathVariable @NotNull final String commentId) {
    return ResponseEntity.ok(
        FindingCommentOutput.from(findingCommentService.deleteComment(commentId)));
  }
}

package io.openaev.rest.finding;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingComment;
import io.openaev.database.model.User;
import io.openaev.database.repository.FindingCommentRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@RequiredArgsConstructor
@Service
public class FindingCommentService {

  private final FindingCommentRepository findingCommentRepository;
  private final FindingRepository findingRepository;
  private final FindingService findingService;
  private final UserService userService;

  @Transactional(readOnly = true)
  public List<FindingComment> findByFinding(@NotBlank final String findingId) {
    return findingCommentRepository.findByFinding_IdOrderByCreationDateDesc(findingId);
  }

  @Transactional
  public FindingComment createComment(
      @NotBlank final String findingId, @NotBlank final String content) {
    Finding finding = findingService.finding(findingId);
    FindingComment comment = new FindingComment();
    comment.setFinding(finding);
    comment.setAuthor(userService.currentUser());
    comment.setContent(content);
    FindingComment saved = findingCommentRepository.save(comment);
    touchFinding(finding);
    return saved;
  }

  // Author-only, enforced here in the service layer (not a capability) - mirrors
  // NotificationTriggerService#canAccess (trigger.getOwner().getId().equals(currentUser.getId())).
  @Transactional
  public FindingComment updateComment(@NotBlank final String id, @NotBlank final String content) {
    FindingComment comment = findById(id);
    requireOwnComment(comment);
    comment.setContent(content);
    comment.setUpdateDate(Instant.now());
    FindingComment saved = findingCommentRepository.save(comment);
    touchFinding(comment.getFinding());
    return saved;
  }

  // No ownership check: DELETE_FINDINGS (already enforced by @AccessControl on the controller)
  // allows deleting any comment, regardless of author.
  @Transactional
  public FindingComment deleteComment(@NotBlank final String id) {
    FindingComment comment = findById(id);
    findingCommentRepository.delete(comment);
    touchFinding(comment.getFinding());
    // Returned (not void) so the audit aspect's output snapshot captures the full deleted content.
    return comment;
  }

  public FindingComment findById(@NotBlank final String id) {
    return findingCommentRepository
        .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
        .orElseThrow(
            () -> new ElementNotFoundException("Finding comment not found with id: " + id));
  }

  // Comments live in their own table (finding_comment), so Hibernate's @UpdateTimestamp on
  // Finding#updateDate never fires from a comment save/delete - explicitly touch the parent
  // Finding so "finding_updated_at" (sortable/filterable in the findings list) reflects comment
  // activity too.
  private void touchFinding(Finding finding) {
    finding.setUpdateDate(Instant.now());
    findingRepository.save(finding);
  }

  private void requireOwnComment(FindingComment comment) {
    User currentUser = userService.currentUser();
    if (!comment.getAuthor().getId().equals(currentUser.getId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You can only edit your own comments");
    }
  }
}

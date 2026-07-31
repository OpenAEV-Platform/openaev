package io.openaev.rest.finding;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingComment;
import io.openaev.database.model.User;
import io.openaev.database.repository.FindingCommentRepository;
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
    return findingCommentRepository.save(comment);
  }

  // Author-only, enforced here in the service layer (not a capability) - mirrors
  // NotificationTriggerService#canAccess (trigger.getOwner().getId().equals(currentUser.getId())).
  @Transactional
  public FindingComment updateComment(@NotBlank final String id, @NotBlank final String content) {
    FindingComment comment = findById(id);
    requireOwnComment(comment);
    comment.setContent(content);
    comment.setUpdateDate(Instant.now());
    return findingCommentRepository.save(comment);
  }

  // No ownership check: DELETE_FINDINGS (already enforced by @AccessControl on the controller)
  // allows deleting any comment, regardless of author.
  @Transactional
  public FindingComment deleteComment(@NotBlank final String id) {
    FindingComment comment = findById(id);
    findingCommentRepository.delete(comment);
    // Returned (not void) so the audit aspect's output snapshot captures the full deleted content.
    return comment;
  }

  public FindingComment findById(@NotBlank final String id) {
    return findingCommentRepository
        .findByIdAndTenantId(id, TenantContext.getCurrentTenant())
        .orElseThrow(
            () -> new ElementNotFoundException("Finding comment not found with id: " + id));
  }

  private void requireOwnComment(FindingComment comment) {
    User currentUser = userService.currentUser();
    if (!comment.getAuthor().getId().equals(currentUser.getId())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "You can only edit your own comments");
    }
  }
}

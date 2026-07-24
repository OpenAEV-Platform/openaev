package io.openaev.service.notification;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.database.model.Notification;
import io.openaev.database.repository.NotificationRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service access to the current user's in-app notifications. */
@RequiredArgsConstructor
@Service
public class NotificationService {

  private final NotificationRepository notificationRepository;
  private final UserService userService;

  @Transactional(readOnly = true)
  public Page<Notification> searchMyNotifications(
      @NotNull final SearchPaginationInput searchPaginationInput) {
    String userId = userService.currentUser().getId();
    Specification<Notification> ownerSpec =
        (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    return buildPaginationJPA(
        (specification, pageable) ->
            notificationRepository.findAll(ownerSpec.and(specification), pageable),
        searchPaginationInput,
        Notification.class);
  }

  @Transactional(readOnly = true)
  public long unreadCount() {
    return notificationRepository.countByUserIdAndReadFalse(userService.currentUser().getId());
  }

  @Transactional
  public Notification markRead(@NotBlank final String id, final boolean read) {
    Notification notification = requireOwnNotification(id);
    notification.setRead(read);
    return notificationRepository.save(notification);
  }

  @Transactional
  public void markAllRead() {
    notificationRepository.markAllAsRead(userService.currentUser().getId());
  }

  @Transactional
  public void delete(@NotBlank final String id) {
    Notification notification = requireOwnNotification(id);
    notificationRepository.delete(notification);
  }

  private Notification requireOwnNotification(String id) {
    return notificationRepository
        .findByIdAndUserId(id, userService.currentUser().getId())
        .orElseThrow(() -> new ElementNotFoundException("Notification not found: " + id));
  }
}

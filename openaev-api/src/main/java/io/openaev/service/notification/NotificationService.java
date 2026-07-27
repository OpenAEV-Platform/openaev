package io.openaev.service.notification;

import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;
import static io.openaev.utils.pagination.SearchUtilsJpa.computeSearchJpa;

import io.openaev.api.notification.NotificationBulkProcessingInput;
import io.openaev.database.model.Notification;
import io.openaev.database.repository.NotificationRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.UserService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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

  /**
   * Bulk delete of the current user's notifications, either from an explicit id list or from a
   * search input (select-all with optional exclusions).
   *
   * @return the ids of the deleted notifications
   */
  @Transactional
  public List<String> bulkDelete(@NotNull final NotificationBulkProcessingInput input) {
    List<String> ids = resolveBulkScope(input).stream().map(Notification::getId).toList();
    // Single bulk DELETE statements (chunked to keep the IN clause bounded)
    // instead of one DELETE per entity.
    chunked(ids).forEach(notificationRepository::deleteAllByIdIn);
    return ids;
  }

  /**
   * Bulk mark read/unread of the current user's notifications, either from an explicit id list or
   * from a search input (select-all with optional exclusions).
   *
   * @return the ids of the updated notifications
   */
  @Transactional
  public List<String> bulkMarkRead(
      @NotNull final NotificationBulkProcessingInput input, final boolean read) {
    List<String> ids = resolveBulkScope(input).stream().map(Notification::getId).toList();
    // Single bulk UPDATE statements (chunked to keep the IN clause bounded)
    // instead of one UPDATE per entity.
    chunked(ids).forEach(chunk -> notificationRepository.setReadByIdIn(chunk, read));
    return ids;
  }

  private static final int BULK_CHUNK_SIZE = 1000;

  /** Splits an id list into bounded chunks for IN-clause based bulk statements. */
  private static List<List<String>> chunked(final List<String> ids) {
    List<List<String>> chunks = new ArrayList<>();
    for (int i = 0; i < ids.size(); i += BULK_CHUNK_SIZE) {
      chunks.add(ids.subList(i, Math.min(i + BULK_CHUNK_SIZE, ids.size())));
    }
    return chunks;
  }

  /**
   * Resolves a bulk scope to the matching notifications, always constrained to the current user
   * (self-service: ids belonging to other users are silently dropped).
   */
  private List<Notification> resolveBulkScope(final NotificationBulkProcessingInput input) {
    boolean hasIds = !CollectionUtils.isEmpty(input.getNotificationIdsToProcess());
    boolean hasSearch = input.getSearchPaginationInput() != null;
    if (hasIds == hasSearch) {
      throw new BadRequestException(
          "Either notification_ids_to_process or search_pagination_input must be provided, and not both at the same time");
    }
    String userId = userService.currentUser().getId();
    Specification<Notification> specification =
        (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    if (hasSearch) {
      // Same specification chain as the list search (filter group + text search), so the bulk
      // scope matches exactly what the user sees in the list.
      specification =
          specification
              .and(
                  FilterUtilsJpa.computeFilterGroupJpa(
                      input.getSearchPaginationInput().getFilterGroup()))
              .and(computeSearchJpa(input.getSearchPaginationInput().getTextSearch()));
    } else {
      List<String> idsToProcess = input.getNotificationIdsToProcess();
      specification = specification.and((root, query, cb) -> root.get("id").in(idsToProcess));
    }
    if (!CollectionUtils.isEmpty(input.getNotificationIdsToIgnore())) {
      List<String> idsToIgnore = input.getNotificationIdsToIgnore();
      specification =
          specification.and((root, query, cb) -> cb.not(root.get("id").in(idsToIgnore)));
    }
    return notificationRepository.findAll(specification);
  }

  private Notification requireOwnNotification(String id) {
    return notificationRepository
        .findByIdAndUserId(id, userService.currentUser().getId())
        .orElseThrow(() -> new ElementNotFoundException("Notification not found: " + id));
  }
}

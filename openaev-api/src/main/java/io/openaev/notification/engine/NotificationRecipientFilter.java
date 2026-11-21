package io.openaev.notification.engine;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.User;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Keeps, among a trigger's recipients, only the users who can read the object an event is about.
 *
 * <p>A trigger watches a resource type, and its recipients are its owner or the users and groups an
 * administrator targeted. None of them is guaranteed to read every object of that type: a scenario
 * or a simulation is read through a capability or a grant on that object. Without this filter the
 * event message (the object's name, and what a semantic event adds to it) would reach a recipient
 * who cannot open the object, in the interface, by email and by webhook.
 *
 * <p>The check is the one the API applies: {@link PermissionService#hasPermissionInTenant}, READ on
 * the object, with the recipient's rights read in the trigger's tenant. It fails closed: a
 * recipient that cannot be loaded, or a check that throws (an object already gone whose parent can
 * no longer be resolved), is dropped.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationRecipientFilter {

  private final PermissionService permissionService;
  private final UserService userService;

  /**
   * The recipients of {@code trigger} who hold READ on {@code entityId}, in their original order.
   * Must run inside the trigger's tenant-scoped transaction, as the engine does.
   */
  public List<String> readersOf(
      ResolvedNotificationTrigger trigger, NotificationResourceCatalog entry, String entityId) {
    String tenantId = trigger.tenantId();
    TxCtx ctx = TxCtx.forTenant(tenantId);
    return trigger.recipientUserIds().stream()
        .filter(userId -> canRead(tenantId, ctx, userId, entry, entityId))
        .toList();
  }

  private boolean canRead(
      String tenantId,
      TxCtx ctx,
      String userId,
      NotificationResourceCatalog entry,
      String entityId) {
    try {
      User user = userService.userWithRoles(userId);
      // The recipient's capabilities, bypass and grants are the ones it holds in the trigger's
      // tenant, whatever tenant is ambient on this engine thread.
      return permissionService.holdsInTenant(
          tenantId,
          () ->
              permissionService.hasPermissionInTenant(
                  ctx, user, entityId, entry.getAccessResourceType(), Action.READ));
    } catch (Exception e) {
      log.debug(
          "Notification recipient {} dropped: READ on {} {} could not be checked",
          userId,
          entry.getAccessResourceType(),
          entityId,
          e);
      return false;
    }
  }
}

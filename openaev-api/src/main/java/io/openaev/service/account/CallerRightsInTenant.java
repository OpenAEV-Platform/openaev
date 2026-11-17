package io.openaev.service.account;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.AuditLogContext;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.GroupRolesInScope;
import jakarta.persistence.EntityManager;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Loads a caller with the roles its groups hold in its ambient tenant, for a permission check in a
 * tenant the request's scope does not hold.
 *
 * <p>The roles of a group are rows of an active table: a load reads the ones of the tenants in the
 * scope of its transaction, and the platform roles. A request loads its caller in its own scope, so
 * on the plain route, whose ambient tenant is the default one while the {@code X-Tenant-Ids} header
 * names another, that load holds none of the caller's default-tenant roles.
 *
 * <p>The user is read in a persistence context of its own, on the request's connection and
 * transaction, with the transaction moved to the ambient tenant for the load and put back after it
 * (see {@link TenantScopedTransaction#executeOnCurrentTransaction}). That move flushes the
 * request's session twice. The first flush, before the scope changes, writes the request's pending
 * changes under the request's scope, at the load and not at the request's own next flush. The
 * second, before the scope is put back, runs under the ambient tenant and finds nothing of the
 * request's to write: the load adds no entity to the request's persistence context. So the load
 * sees what the transaction wrote, takes no second connection, and leaves the scope of the
 * request's later reads as it was. The work loads the caller's user and what any load of a user
 * loads eagerly with it: its groups with their tenant, grants, members, roles and markings. Those
 * loads fire the entity listeners on the request thread, so the audit before-snapshots they store
 * are put back afterwards as the request's own reads left them ({@link
 * AuditLogContext#withBeforeSnapshotsRestored}). The user returned is detached, with only those
 * associations loaded, and serves a permission decision: no caller returns it to a client.
 */
@Component
@RequiredArgsConstructor
public class CallerRightsInTenant {

  private final TenantScopedTransaction tenantTx;
  private final EntityManager entityManager;

  /**
   * Loads {@code caller} again, in its ambient tenant ({@link User#scopedTenantId()}): the method
   * takes no tenant, so it reads no tenant but the ambient one. It does not check which user it is
   * given; an architecture test lets only {@code PermissionService} call this class, which passes
   * the caller of its own permission decision.
   *
   * @throws ElementNotFoundException when {@code caller} has no user row
   */
  public User loadInAmbientTenant(User caller) {
    String callerId = caller.getId();
    return tenantTx.executeOnCurrentTransaction(
        TxCtx.forTenant(caller.scopedTenantId()),
        () -> AuditLogContext.withBeforeSnapshotsRestored(() -> loadInOwnSession(callerId)));
  }

  /**
   * Reads again, in the ambient tenant ({@link User#scopedTenantId()}), the roles of the groups of
   * {@code user} that belong to that tenant, in the persistence context that holds {@code user}.
   * One query reads the roles of all those groups ({@link GroupRolesInScope}) and only the groups
   * whose roles differ from the ones the user holds are refreshed. For a response that describes
   * the user in the ambient tenant: its capabilities and flags are derived from those roles, and a
   * load that ran in a scope without the ambient tenant read none of them. The transaction moves to
   * the ambient tenant for the reads and back, as {@link #loadInAmbientTenant} does, and the audit
   * before-snapshots are put back after them.
   */
  public void loadGroupRolesInAmbientTenant(User user) {
    String ambientTenant = user.scopedTenantId();
    tenantTx.executeOnCurrentTransaction(
        TxCtx.forTenant(ambientTenant),
        () ->
            AuditLogContext.withBeforeSnapshotsRestored(
                () -> {
                  List<Group> ambientGroups =
                      user.getUnscopedGroups().stream()
                          .filter(
                              group ->
                                  group.getTenant() != null
                                      && ambientTenant.equals(group.getTenant().getId()))
                          .toList();
                  GroupRolesInScope.withDifferingRoles(entityManager, ambientGroups)
                      .forEach(entityManager::refresh);
                  return null;
                }));
  }

  private User loadInOwnSession(String userId) {
    try (Session session =
        entityManager
            .unwrap(Session.class)
            .sessionWithOptions()
            .connection()
            .flushMode(FlushMode.MANUAL)
            .openSession()) {
      session.setDefaultReadOnly(true);
      User user = session.find(User.class, userId);
      if (user == null) {
        throw new ElementNotFoundException("User not found with id: " + userId);
      }
      return user;
    }
  }
}

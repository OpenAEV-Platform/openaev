package io.openaev.service.account;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.User;
import io.openaev.rest.exception.ElementNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

/**
 * Loads a caller with the roles its groups hold in one tenant, for a permission check in a tenant
 * the request's scope does not hold.
 *
 * <p>The roles of a group are rows of an active table: a load reads the ones of the tenants in the
 * scope of its transaction, and the platform roles. A request loads its caller in its own scope, so
 * on the plain route, whose ambient tenant is the default one while the {@code X-Tenant-Ids} header
 * names another, that load holds none of the caller's default-tenant roles.
 *
 * <p>The user is read in a persistence context of its own, on the request's connection and
 * transaction, with the transaction moved to {@code tenantId} for the load and put back after it
 * (see {@link TenantScopedTransaction#executeOnCurrentTransaction}). So the load sees what the
 * transaction wrote, takes no second connection, and leaves the request's session and the scope of
 * its later reads as they were. The work loads the caller's user and what any load of a user loads
 * eagerly with it: its groups with their tenant, grants, members, roles and markings. The user
 * returned is detached, with only those associations loaded, and serves a permission decision: no
 * caller returns it to a client.
 */
@Component
@RequiredArgsConstructor
public class CallerRightsInTenant {

  private final TenantScopedTransaction tenantTx;
  private final EntityManager entityManager;

  /**
   * @throws ElementNotFoundException when no user has {@code userId}
   */
  public User load(String userId, String tenantId) {
    return tenantTx.executeOnCurrentTransaction(
        TxCtx.forTenant(tenantId), () -> loadInOwnSession(userId));
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
      User user = session.get(User.class, userId);
      if (user == null) {
        throw new ElementNotFoundException("User not found with id: " + userId);
      }
      return user;
    }
  }
}

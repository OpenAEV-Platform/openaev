package io.openaev.utilstest;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Saves and deletes group fixtures the way production writes them, so the database accepts them
 * once {@code groups} is tenant-active: a tenant group in the scope of its own tenant ({@link
 * RowTenantScope}), a platform group ({@code tenant == null}) under the platform-write marker. A
 * save also writes the group's membership bag, which the database checks the same way.
 */
@Component
@RequiredArgsConstructor
public class GroupFixtureWriter {

  private final GroupRepository groupRepository;
  private final RowTenantScope rowTenantScope;
  private final TenantScopedTransaction tenantScopedTransaction;
  private final EntityManager entityManager;

  public Group save(Group group) {
    Tenant tenant = group.getTenant();
    if (tenant == null) {
      return asPlatformWrite(() -> groupRepository.save(group));
    }
    AtomicReference<Group> saved = new AtomicReference<>();
    rowTenantScope.write(tenant.getId(), () -> saved.set(groupRepository.save(group)));
    return saved.get();
  }

  public void delete(Group group) {
    Tenant tenant = group.getTenant();
    if (tenant == null) {
      asPlatformWrite(
          () -> {
            deleteWithItsMemberships(group.getId());
            return null;
          });
      return;
    }
    rowTenantScope.write(tenant.getId(), () -> deleteWithItsMemberships(group.getId()));
  }

  private void deleteWithItsMemberships(String groupId) {
    groupRepository
        .findById(groupId)
        .ifPresent(
            group -> {
              // Each member's own bag maps the same join table: a member still listing the group
              // would fail the flush on a deleted instance
              group.getUsers().forEach(user -> user.getUnscopedGroups().remove(group));
              groupRepository.delete(group);
            });
  }

  private Group asPlatformWrite(Supplier<Group> write) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      return tenantScopedTransaction.executeAsPlatformWrite(
          TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), write);
    }
    String previousMarker = platformWriteMarker();
    tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
    Group result = write.get();
    // The statements run at the flush, so the marker is restored after it: the rest of the test
    // transaction does not write as a platform write
    entityManager.flush();
    restorePlatformWriteMarker(previousMarker);
    return result;
  }

  private String platformWriteMarker() {
    return (String)
        entityManager
            .createNativeQuery("SELECT coalesce(current_setting('app.platform_write', true), '')")
            .setFlushMode(FlushModeType.COMMIT)
            .getSingleResult();
  }

  private void restorePlatformWriteMarker(String marker) {
    entityManager
        .createNativeQuery("SELECT set_config('app.platform_write', :marker, true)")
        .setParameter("marker", marker)
        .setFlushMode(FlushModeType.COMMIT)
        .getResultList();
  }
}

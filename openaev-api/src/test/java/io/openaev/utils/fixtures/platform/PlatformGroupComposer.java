package io.openaev.utils.fixtures.platform;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.GroupRepository;
import io.openaev.utils.fixtures.composers.ComposerBase;
import io.openaev.utils.fixtures.composers.InnerComposerBase;
import io.openaev.utils.fixtures.composers.PlatformRoleComposer;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PlatformGroupComposer extends ComposerBase<Group> {

  @Autowired private GroupRepository groupRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  public class Composer extends InnerComposerBase<Group> {

    private final Group group;
    private final List<PlatformRoleComposer.Composer> roleComposers = new ArrayList<>();

    public Composer(Group group) {
      this.group = group;
    }

    public PlatformGroupComposer.Composer withRole(PlatformRoleComposer.Composer roleComposer) {
      this.roleComposers.add(roleComposer);
      this.group.getRoles().add(roleComposer.get());
      return this;
    }

    @Override
    public PlatformGroupComposer.Composer persist() {
      roleComposers.forEach(PlatformRoleComposer.Composer::persist);
      // A platform group (no tenant) is writable only in a platform-write transaction
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        String previousMarker = platformWriteMarker();
        tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
        groupRepository.save(group);
        // The INSERT runs at the flush, so the marker is restored after it: the rest of the test
        // transaction does not write as a platform write
        entityManager.flush();
        restorePlatformWriteMarker(previousMarker);
      } else {
        tenantScopedTransaction.executeAsPlatformWrite(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), () -> groupRepository.save(group));
      }
      return this;
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

    @Override
    public PlatformGroupComposer.Composer delete() {
      group.getUsers().forEach(user -> user.getUnscopedGroups().remove(group));
      groupRepository.delete(group);
      return null;
    }

    @Override
    public Group get() {
      return this.group;
    }
  }

  public PlatformGroupComposer.Composer forPlatformGroup(Group group) {
    generatedItems.add(group);
    return new PlatformGroupComposer.Composer(group);
  }
}

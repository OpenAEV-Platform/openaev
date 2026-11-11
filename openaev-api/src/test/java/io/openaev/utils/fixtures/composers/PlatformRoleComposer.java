package io.openaev.utils.fixtures.composers;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.RoleRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PlatformRoleComposer extends ComposerBase<Role> {

  @Autowired private RoleRepository roleRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;

  public class Composer extends InnerComposerBase<Role> {
    private final Role role;

    public Composer(Role role) {
      this.role = role;
    }

    @Override
    public PlatformRoleComposer.Composer persist() {
      // A platform role (no tenant) is writable only in a platform-write transaction
      if (TransactionSynchronizationManager.isActualTransactionActive()) {
        String previousMarker = platformWriteMarker();
        tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
        roleRepository.save(role);
        // The INSERT runs at the flush, so the marker is restored after it: the rest of the test
        // transaction does not write as a platform write
        entityManager.flush();
        restorePlatformWriteMarker(previousMarker);
      } else {
        tenantScopedTransaction.executeAsPlatformWrite(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), () -> roleRepository.save(role));
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
    public PlatformRoleComposer.Composer delete() {
      roleRepository.delete(role);
      return null;
    }

    @Override
    public Role get() {
      return this.role;
    }
  }

  public PlatformRoleComposer.Composer forPlatformRole(Role role) {
    generatedItems.add(role);
    return new PlatformRoleComposer.Composer(role);
  }
}

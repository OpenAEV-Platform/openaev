package io.openaev.utils.fixtures.composers;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.RoleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class PlatformRoleComposer extends ComposerBase<Role> {

  @Autowired private RoleRepository roleRepository;
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
        tenantScopedTransaction.setPlatformWriteOnCurrentTransaction();
        roleRepository.save(role);
      } else {
        tenantScopedTransaction.executeAsPlatformWrite(
            TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID), () -> roleRepository.save(role));
      }
      return this;
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

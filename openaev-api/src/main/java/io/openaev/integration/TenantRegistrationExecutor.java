package io.openaev.integration;

import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManagerException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Executes built-in tenant registration. Provides two entry points:
 *
 * <ul>
 *   <li>{@link #registerForTenant(Tenant)} — startup path: switches tenant context, registers, then
 *       flushes/clears/restores.
 *   <li>{@link #registerForTenant(Tenant)} — tenant creation path: just registers (caller manages
 *       context).
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantRegistrationExecutor {

  private final List<BuiltinTenantRegistrable> builtinRegistrables;

  public void registerForTenant(Tenant tenant) throws DependenciesManagerException {
    for (BuiltinTenantRegistrable registrable : builtinRegistrables) {
      try {
        registrable.registerForTenant(tenant.getId());
      } catch (Exception e) {
        throw new DependenciesManagerException(
            "Failed to register built-in connector %s for tenant %s"
                .formatted(registrable.getClass().getSimpleName(), tenant.getName()),
            e);
      }
    }
    log.info(
        "Successfully registered {} built-in connector(s) for tenant '{}'",
        builtinRegistrables.size(),
        tenant.getName());
  }
}

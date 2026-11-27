package io.openaev.opencti.connectors.service;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.service.account.ReservedServiceAccount.OPENCTI_CONNECTOR;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.service.TenantRoleService;
import io.openaev.service.UserService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles cleanup of pre-multi-tenant OpenCTI connector service accounts.
 *
 * <p>Before multi-tenancy, a single connector user was created with email {@code
 * connector-<baseId>@openaev.invalid}. After multi-tenancy, each tenant gets its own connector user
 * with email {@code connector-opencti-<baseId>:<tenantId>@openaev.invalid}.
 *
 * <p>This class detects and deletes the legacy user so the new tenant-scoped user can be created
 * cleanly.
 *
 * <p><b>TODO: remove this class once all deployments have been migrated to multi-tenant.</b>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LegacyOpenCTIConnectorMigration {

  static final String LEGACY_CONNECTOR_BASE_ID = "68949a7b-c1c2-4649-b3de-7db804ba02bb";
  static final String LEGACY_EMAIL_PATTERN = "connector-%s@openaev.invalid";

  private final UserService userService;
  private final TenantRoleService tenantRoleService;
  private final GroupRepository groupRepository;
  private final TenantScopedTransaction tenantTx;

  /** Tracks whether the legacy cleanup has already been performed (or confirmed unnecessary). */
  private volatile boolean migrationDone = false;

  /**
   * Deletes the legacy (pre-multi-tenant) connector service account if it still exists. This method
   * is idempotent and short-circuits after the first successful check.
   *
   * <p>The caller may be running with the transaction scoped to a tenant other than the default one
   * (a connector of a non-default tenant being provisioned): the legacy account lives in the
   * default tenant, so the cleanup runs in its own default-tenant transaction rather than joining
   * the caller's scope, which {@code roles} (a v2-active table) would otherwise refuse to read.
   *
   * @param newEmail the new tenant-scoped email, used only for logging
   */
  public void deleteLegacyConnectorIfExists(String newEmail) {
    if (migrationDone) {
      return;
    }
    tenantTx.executeNew(
        TxCtx.forTenant(DEFAULT_TENANT_UUID), () -> deleteLegacyConnector(newEmail));
    migrationDone = true;
  }

  private void deleteLegacyConnector(String newEmail) {
    String legacyEmail = LEGACY_EMAIL_PATTERN.formatted(LEGACY_CONNECTOR_BASE_ID);
    Optional<User> legacyUser = userService.findByEmailIgnoreCase(legacyEmail);

    if (legacyUser.isPresent()) {
      log.info(
          "Deleting legacy connector service account {} — replaced by tenant-scoped account {}",
          legacyEmail,
          newEmail);
      userService.delete(legacyUser.get().getId());
      if (groupRepository.existsById(OPENCTI_CONNECTOR.access().groupId())) {
        groupRepository.deleteById(OPENCTI_CONNECTOR.access().groupId());
      }
      tenantRoleService.delete(DEFAULT_TENANT_UUID, OPENCTI_CONNECTOR.access().roleId());
    }
  }
}

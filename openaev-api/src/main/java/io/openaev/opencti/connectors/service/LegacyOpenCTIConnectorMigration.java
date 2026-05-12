package io.openaev.opencti.connectors.service;

import static io.openaev.opencti.connectors.Constants.PROCESS_STIX_GROUP_ID;
import static io.openaev.opencti.connectors.Constants.PROCESS_STIX_ROLE_ID;

import io.openaev.database.model.User;
import io.openaev.database.repository.GroupRepository;
import io.openaev.service.RoleService;
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

  private static final String LEGACY_CONNECTOR_BASE_ID = "68949a7b-c1c2-4649-b3de-7db804ba02bb";
  private static final String LEGACY_EMAIL_PATTERN = "connector-%s@openaev.invalid";

  private final UserService userService;
  private final RoleService roleService;
  private final GroupRepository groupRepository;

  /** Tracks whether the legacy cleanup has already been performed (or confirmed unnecessary). */
  private volatile boolean migrationDone = false;

  /**
   * Deletes the legacy (pre-multi-tenant) connector service account if it still exists. This method
   * is idempotent and short-circuits after the first successful check.
   *
   * @param newEmail the new tenant-scoped email, used only for logging
   */
  public void deleteLegacyConnectorIfExists(String newEmail) {
    if (migrationDone) {
      return;
    }

    String legacyEmail = LEGACY_EMAIL_PATTERN.formatted(LEGACY_CONNECTOR_BASE_ID);
    Optional<User> legacyUser = userService.findByEmailIgnoreCase(legacyEmail);

    if (legacyUser.isPresent()) {
      log.info(
          "Deleting legacy connector service account {} — replaced by tenant-scoped account {}",
          legacyEmail,
          newEmail);
      userService.delete(legacyUser.get().getId());
      groupRepository.deleteById(PROCESS_STIX_GROUP_ID);
      roleService.deleteRole(PROCESS_STIX_ROLE_ID);
    }
    migrationDone = true;
  }
}

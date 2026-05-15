package io.openaev.opencti.connectors.service;

import static io.openaev.opencti.connectors.Constants.*;

import io.openaev.api.groups.dto.TenantGroupCreateInput;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.service.RoleService;
import io.openaev.service.TenantGroupService;
import io.openaev.service.UserService;
import io.openaev.service.tenants.TenantUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class PrivilegeService {

  public static final String CONNECTOR_EMAIL_PATTERN = "connector-opencti-%s@openaev.invalid";
  private static final String CONNECTOR_LASTNAME = "OpenCTI Connector";

  private final RoleService roleService;
  private final TenantGroupService tenantGroupService;
  private final UserService userService;
  private final TenantUserService tenantUserService;
  private final LegacyOpenCTIConnectorMigration legacyOpenCTIConnectorMigration;

  /**
   * Ensures a privileged technical user exists for the given OpenCTI connector. Creates or updates
   * the user, its group, role, and tenant attachment as needed.
   */
  public void ensurePrivilegedUserExistsForConnector(ConnectorBase connector) {
    String email = CONNECTOR_EMAIL_PATTERN.formatted(connector.getId());

    // TODO: remove once all deployments have been migrated to multi-tenant
    legacyOpenCTIConnectorMigration.deleteLegacyConnectorIfExists(email);

    Group group =
        createWellKnownGroupWithRole(
            createWellKnownRole(connector.getTenantId()), connector.getTenantId());
    Optional<User> connectorUser =
        userService.findByTokenAndTenantId(connector.getToken(), connector.getTenantId());
    Optional<User> existingEmailUser = userService.findByEmailIgnoreCase(email);

    if (connectorUser.isPresent()) {
      // Token-matched user already exists — update its attributes
      applyConnectorAttributes(connectorUser.get(), connector, email, group);
      userService.saveUser(connectorUser.get());
      tenantUserService.attachToTenant(connectorUser.get().getId(), connector.getTenantId());
    } else if (existingEmailUser.isPresent()) {
      // Email-matched user exists but has no token — reuse and attach token
      log.warn(
          "User with email {} already exists, but no token found. Reusing existing user.",
          existingEmailUser.get().getEmail());
      existingEmailUser
          .get()
          .setTokens(
              new ArrayList<>(
                  List.of(
                      userService.createUserToken(existingEmailUser.get(), connector.getToken()))));
      applyConnectorAttributes(existingEmailUser.get(), connector, email, group);
      userService.saveUser(existingEmailUser.get());
      tenantUserService.attachToTenant(existingEmailUser.get().getId(), connector.getTenantId());
    } else {
      // No user exists — create one
      User user =
          userService.createInternalUser(
              email, connector.getName(), CONNECTOR_LASTNAME, false, connector.getToken());
      user.setGroups(new ArrayList<>(List.of(group)));
      User savedUser = userService.saveUser(user);
      tenantUserService.attachToTenant(savedUser.getId(), connector.getTenantId());
    }
  }

  // -- PRIVATE --

  private void applyConnectorAttributes(
      User user, ConnectorBase connector, String email, Group group) {
    user.setFirstname(connector.getName());
    user.setLastname(CONNECTOR_LASTNAME);
    user.setEmail(email);
    user.setAdmin(false);
    user.setGroups(new ArrayList<>(List.of(group)));
  }

  private Role createWellKnownRole(String tenantId) {
    String roleId =
        UUID.nameUUIDFromBytes((UUID.fromString(PROCESS_STIX_ROLE_ID) + ":" + tenantId).getBytes())
            .toString();
    Optional<Role> processStixRole = roleService.findById(roleId);
    if (processStixRole.isEmpty()) {
      return roleService.createRole(
          roleId,
          PROCESS_STIX_ROLE_NAME,
          PROCESS_STIX_ROLE_DESCRIPTION,
          PROCESS_STIX_ROLE_CAPABILITIES,
          tenantId);
    } else {
      return roleService.updateRole(
          roleId,
          PROCESS_STIX_ROLE_NAME,
          PROCESS_STIX_ROLE_DESCRIPTION,
          PROCESS_STIX_ROLE_CAPABILITIES);
    }
  }

  private Group createWellKnownGroupWithRole(Role role, String tenantId) {
    String groupId =
        UUID.nameUUIDFromBytes((UUID.fromString(PROCESS_STIX_GROUP_ID) + ":" + tenantId).getBytes())
            .toString();
    Optional<Group> processStixGroup = tenantGroupService.findById(groupId);

    TenantGroupCreateInput input = new TenantGroupCreateInput();
    input.setName(PROCESS_STIX_GROUP_NAME);
    input.setDescription(PROCESS_STIX_GROUP_DESCRIPTION);
    input.setDefaultUserAssignation(false);

    List<Role> roles = new ArrayList<>(List.of(role));
    if (processStixGroup.isPresent()) {
      return tenantGroupService.updateGroupInfoWithRoles(processStixGroup.get(), input, roles);
    } else {
      return tenantGroupService.createGroupWithRole(groupId, input, roles, tenantId);
    }
  }
}

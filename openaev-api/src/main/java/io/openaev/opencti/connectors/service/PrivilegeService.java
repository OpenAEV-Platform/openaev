package io.openaev.opencti.connectors.service;

import static io.openaev.opencti.connectors.Constants.*;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.service.*;
import io.openaev.service.tenants.TenantUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class PrivilegeService extends AbstractPrivilegeService {

  public static final String CONNECTOR_EMAIL_PATTERN = "connector-opencti-%s@openaev.invalid";
  private static final String CONNECTOR_LASTNAME = "OpenCTI Connector";

  LegacyOpenCTIConnectorMigration legacyOpenCTIConnectorMigration;

  @Autowired
  public PrivilegeService(
      TenantRoleService tenantRoleService,
      TenantGroupService tenantGroupService,
      UserService userService,
      TenantUserService tenantUserService,
      LegacyOpenCTIConnectorMigration legacyOpenCTIConnectorMigration) {
    super(tenantRoleService, tenantGroupService, userService, tenantUserService);
    this.legacyOpenCTIConnectorMigration = legacyOpenCTIConnectorMigration;
  }

  @Override
  protected String getRoleId() {
    return PROCESS_STIX_ROLE_ID;
  }

  @Override
  protected String getRoleName() {
    return PROCESS_STIX_ROLE_NAME;
  }

  @Override
  protected String getRoleDescription() {
    return PROCESS_STIX_ROLE_DESCRIPTION;
  }

  @Override
  protected Set<Capability> getRoleCapabilities() {
    return PROCESS_STIX_ROLE_CAPABILITIES;
  }

  @Override
  protected String getGroupId() {
    return PROCESS_STIX_GROUP_ID;
  }

  @Override
  protected String getGroupName() {
    return PROCESS_STIX_GROUP_NAME;
  }

  @Override
  protected String getGroupDescription() {
    return PROCESS_STIX_GROUP_DESCRIPTION;
  }

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
      applyUserServiceAttributes(
          connectorUser.get(), connector.getName(), CONNECTOR_LASTNAME, email, group);
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
      applyUserServiceAttributes(
          existingEmailUser.get(), connector.getName(), CONNECTOR_LASTNAME, email, group);
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
}

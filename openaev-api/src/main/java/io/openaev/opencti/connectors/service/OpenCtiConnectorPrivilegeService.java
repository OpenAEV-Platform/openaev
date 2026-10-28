package io.openaev.opencti.connectors.service;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.service.*;
import io.openaev.service.account.AbstractPrivilegeService;
import io.openaev.service.account.ReservedServiceAccount;
import io.openaev.service.account.WellKnownAccess;
import io.openaev.service.tenants.TenantUserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class OpenCtiConnectorPrivilegeService extends AbstractPrivilegeService {

  private static final String CONNECTOR_LASTNAME = "OpenCTI Connector";

  LegacyOpenCTIConnectorMigration legacyOpenCTIConnectorMigration;
  private final TenantScopedTransaction tenantTx;

  @Autowired
  public OpenCtiConnectorPrivilegeService(
      TenantRoleService tenantRoleService,
      TenantGroupService tenantGroupService,
      UserService userService,
      TenantUserService tenantUserService,
      LegacyOpenCTIConnectorMigration legacyOpenCTIConnectorMigration,
      TenantScopedTransaction tenantTx) {
    super(tenantRoleService, tenantGroupService, userService, tenantUserService);
    this.legacyOpenCTIConnectorMigration = legacyOpenCTIConnectorMigration;
    this.tenantTx = tenantTx;
  }

  @Override
  protected WellKnownAccess wellKnownAccess() {
    return ReservedServiceAccount.OPENCTI_CONNECTOR.access();
  }

  /**
   * Ensures a privileged technical user exists for the given OpenCTI connector. Creates or updates
   * the user, its group, role, and tenant attachment as needed.
   */
  public void ensurePrivilegedUserExistsForConnector(ConnectorBase connector) {
    // The whole chain below runs from a Quartz job (OpenCTIConnectorRegisterPingJob), so nothing
    // upstream carries a tenant scope. The well-known group's `markings` is an eager association
    // over the v2-active `marking_definitions` table: with no scope the inspector fail-closes and
    // the group is ensured granting no marking. The connector names its own tenant, which is the
    // only correct scope here - `marking_definitions.tenant_id` is NOT NULL, so a group may only
    // grant markings of its own tenant and a wider scope could only bring another tenant's in.
    // setScopeOnCurrentTransaction rather than execute(): this method is @Transactional, the
    // transaction is already open and is the unit of work the register/ping flow commits, while
    // the OpenCTI HTTP calls deliberately happen after it, outside any transaction.
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(connector.getTenantId()));

    String email =
        ReservedServiceAccount.OPENCTI_CONNECTOR.emailPattern().formatted(connector.getId());

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

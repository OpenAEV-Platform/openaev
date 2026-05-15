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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrivilegeService {

  private static final String CONNECTOR_EMAIL_PATTERN = "connector-%s@openaev.invalid";
  private static final String CONNECTOR_LASTNAME = "OpenCTI Connector";

  private final RoleService roleService;
  private final TenantGroupService tenantGroupService;
  private final UserService userService;

  @Transactional
  public void ensurePrivilegedUserExistsForConnector(ConnectorBase connector) {
    Group group = createWellKnownGroupWithRole(createWellKnownRole());
    String email = CONNECTOR_EMAIL_PATTERN.formatted(connector.getId());

    Optional<User> connectorUser = userService.findByToken(connector.getToken());
    Optional<User> existingEmailUser = userService.findByEmailIgnoreCase(email);

    if (connectorUser.isPresent()) {
      // Token-matched user already exists — update its attributes
      applyConnectorAttributes(connectorUser.get(), connector, email, group);
      userService.saveUser(connectorUser.get());
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
    } else {
      // No user exists — create one
      User user =
          userService.createInternalUser(
              email, connector.getName(), CONNECTOR_LASTNAME, false, connector.getToken());
      user.setGroups(new ArrayList<>(List.of(group)));
      userService.saveUser(user);
    }
  }

  private void applyConnectorAttributes(
      User user, ConnectorBase connector, String email, Group group) {
    user.setFirstname(connector.getName());
    user.setLastname(CONNECTOR_LASTNAME);
    user.setEmail(email);
    user.setAdmin(false);
    user.setGroups(new ArrayList<>(List.of(group)));
  }

  private Role createWellKnownRole() {
    Optional<Role> processStixRole = roleService.findById(PROCESS_STIX_ROLE_ID);
    if (processStixRole.isEmpty()) {
      processStixRole =
          Optional.of(
              roleService.createRole(
                  PROCESS_STIX_ROLE_ID,
                  PROCESS_STIX_ROLE_NAME,
                  PROCESS_STIX_ROLE_DESCRIPTION,
                  PROCESS_STIX_ROLE_CAPABILITIES));
    } else {
      processStixRole =
          Optional.of(
              roleService.updateRole(
                  PROCESS_STIX_ROLE_ID,
                  PROCESS_STIX_ROLE_NAME,
                  PROCESS_STIX_ROLE_DESCRIPTION,
                  PROCESS_STIX_ROLE_CAPABILITIES));
    }
    return processStixRole.get();
  }

  private Group createWellKnownGroupWithRole(Role role) {
    Optional<Group> processStixGroup = tenantGroupService.findById(PROCESS_STIX_GROUP_ID);

    TenantGroupCreateInput input = new TenantGroupCreateInput();
    input.setName(PROCESS_STIX_GROUP_NAME);
    input.setDescription(PROCESS_STIX_GROUP_DESCRIPTION);
    input.setDefaultUserAssignation(false);

    processStixGroup =
        processStixGroup
            .map(
                group ->
                    tenantGroupService.updateGroupInfoWithRoles(
                        group, input, new ArrayList<>(List.of(role))))
            .or(
                () ->
                    Optional.of(
                        tenantGroupService.createGroupWithRole(
                            PROCESS_STIX_GROUP_ID, input, new ArrayList<>(List.of(role)))));
    return processStixGroup.get();
  }
}

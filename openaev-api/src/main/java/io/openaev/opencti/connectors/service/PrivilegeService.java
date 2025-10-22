package io.openaev.opencti.connectors.service;

import static io.openaev.opencti.connectors.Constants.*;

import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.User;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.rest.group.form.GroupCreateInput;
import io.openaev.rest.user.form.user.CreateUserInput;
import io.openaev.rest.user.form.user.UpdateUserInput;
import io.openaev.service.GroupService;
import io.openaev.service.RoleService;
import io.openaev.service.UserService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrivilegeService {
  private final RoleService roleService;
  private final GroupService groupService;
  private final UserService userService;

  @Transactional
  public void ensurePrivilegedUserExistsForConnector(ConnectorBase connector) {
    Group group = createWellKnownGroupWithRole(createWellKnownRole());

    Optional<User> connectorUser = userService.findByToken(connector.getToken());
    if (connectorUser.isEmpty()) {
      CreateUserInput input = new CreateUserInput();
      input.setAdmin(false);
      input.setFirstname(connector.getName());
      input.setLastname("OpenCTI Connector");
      input.setToken(connector.getToken());
      input.setEmail("connector-%s@openaev.invalid".formatted(connector.getId()));
      User u = userService.createUser(input, 1); // magic number; Active
      u.setGroups(new ArrayList<>(List.of(group)));
      userService.updateUser(u);
    } else {
      UpdateUserInput input = new UpdateUserInput();
      input.setAdmin(false);
      input.setFirstname(connector.getName());
      input.setLastname("OpenCTI Connector");
      input.setEmail("connector-%s@openaev.invalid".formatted(connector.getId()));
      connectorUser.get().setGroups(new ArrayList<>(List.of(group)));
      userService.updateUser(connectorUser.get(), input);
    }
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
    Optional<Group> processStixGroup = groupService.findById(PROCESS_STIX_GROUP_ID);

    GroupCreateInput input = new GroupCreateInput();
    input.setName(PROCESS_STIX_GROUP_NAME);
    input.setDescription(PROCESS_STIX_GROUP_DESCRIPTION);
    input.setDefaultUserAssignation(false);

    processStixGroup =
        processStixGroup
            .map(
                group ->
                    groupService.updateGroupInfoWithRoles(
                        group, input, new ArrayList<>(List.of(role))))
            .or(
                () ->
                    Optional.of(
                        groupService.createGroupWithRole(
                            PROCESS_STIX_GROUP_ID, input, new ArrayList<>(List.of(role)))));
    return processStixGroup.get();
  }
}

package io.openbas.utils.mockUser;

import static io.openbas.database.model.Grant.GRANT_TYPE.PLANNER;
import static io.openbas.service.UserService.buildAuthenticationToken;

import io.openbas.database.model.*;
import io.openbas.database.repository.GrantRepository;
import io.openbas.database.repository.GroupRepository;
import io.openbas.database.repository.RoleRepository;
import io.openbas.database.repository.UserRepository;
import io.openbas.database.specification.GroupSpecification;
import io.openbas.database.specification.RoleSpecification;
import jakarta.annotation.PreDestroy;
import jakarta.transaction.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithSecurityContextFactory;
import org.springframework.stereotype.Component;

@Component
public class WithMockUserFullPermissionsSecurityContextFactory
    implements WithSecurityContextFactory<WithMockUserFullPermissions>,
        ApplicationListener<ContextRefreshedEvent> {

  public static final String MOCK_USER_FULL_PERMISSION_EMAIL = "fullPermissions@openbas.io";
  public static final String MOCK_ROLE_FULL_PERMISSION_NAME = "FullPermissionsRole";
  public static final String MOCK_FULL_PERMISSION_GROUP = "Mock Full Permissions group";
  @Autowired private GrantRepository grantRepository;
  @Autowired private GroupRepository groupRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private UserRepository userRepository;

  private volatile boolean initialized = false;

  @Override
  public SecurityContext createSecurityContext(WithMockUserFullPermissions customUser) {
    User user = this.userRepository.findByEmailIgnoreCase(customUser.email()).orElseThrow();
    Authentication authentication = buildAuthenticationToken(user);
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    return context;
  }

  @Override
  @Transactional
  public void onApplicationEvent(ContextRefreshedEvent event) {
    if (!initialized) {
      createPlannerMockUser();
      initialized = true;
    }
  }

  @PreDestroy
  public void preDestroy() {
    this.userRepository.deleteById(
        this.userRepository
            .findByEmailIgnoreCase(MOCK_USER_FULL_PERMISSION_EMAIL)
            .orElseThrow()
            .getId());
    this.groupRepository.deleteById(
        this.groupRepository
            .findOne(GroupSpecification.fromName(MOCK_FULL_PERMISSION_GROUP))
            .orElseThrow()
            .getId());
    ;
    this.roleRepository.deleteById(
        this.roleRepository
            .findOne(RoleSpecification.fromName(MOCK_ROLE_FULL_PERMISSION_NAME))
            .orElseThrow()
            .getId());
  }

  @Transactional
  public void createPlannerMockUser() {
    Optional<User> userOpt =
        this.userRepository.findByEmailIgnoreCase(MOCK_USER_FULL_PERMISSION_EMAIL);
    if (userOpt.isPresent() && userOpt.get().isPlanner()) {
      return;
    }
    // Create role
    Optional<Role> roleOpt =
        this.roleRepository.findOne(RoleSpecification.fromName(MOCK_ROLE_FULL_PERMISSION_NAME));
    Role role;
    if (roleOpt.isEmpty()) {
      Role newRole = new Role();
      newRole.setName(MOCK_ROLE_FULL_PERMISSION_NAME);
      newRole.setCapabilities(Arrays.stream(Capability.values()).collect(Collectors.toSet()));
      role = this.roleRepository.save(newRole);
    } else {
      role = roleOpt.get(); // Initialize lazy collections if needed
      Hibernate.initialize(role.getCapabilities());
    }

    // Create group
    Optional<Group> groupOpt =
        this.groupRepository.findOne(GroupSpecification.fromName(MOCK_FULL_PERMISSION_GROUP));
    Group group;
    if (groupOpt.isEmpty()) {
      Group newGroup = new Group();
      newGroup.setName(MOCK_FULL_PERMISSION_GROUP);
      newGroup.setRoles(List.of(role));
      group = this.groupRepository.save(newGroup);
      // Create grant
      Grant grant = new Grant();
      grant.setName(PLANNER);
      grant.setGroup(group);
      this.grantRepository.save(grant);
    } else {
      group = groupOpt.get();
    }
    // Create user
    if (userOpt.isEmpty()) {
      User user = new User();
      user.setGroups(List.of(group));
      user.setEmail(MOCK_USER_FULL_PERMISSION_EMAIL);
      this.userRepository.save(user);
    } else if (!userOpt.get().isPlanner()) {
      userOpt.get().setGroups(List.of(group));
      this.userRepository.save(userOpt.get());
    }
  }
}

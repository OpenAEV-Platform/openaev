package io.openaev.service.account;

import static io.openaev.service.account.Constants.*;

import io.openaev.database.model.Capability;
import io.openaev.database.model.Group;
import io.openaev.database.model.User;
import io.openaev.service.*;
import io.openaev.service.tenants.TenantUserService;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class ServiceAccountPrivilegeService extends AbstractPrivilegeService {
  public static final String SERVICE_EMAIL_PATTERN = "service-%s@openaev.invalid";
  private static final String SERVICE_FIRSTNAME = "service";

  @Autowired
  public ServiceAccountPrivilegeService(
      TenantRoleService tenantRoleService,
      TenantGroupService tenantGroupService,
      UserService userService,
      TenantUserService tenantUserService) {
    super(tenantRoleService, tenantGroupService, userService, tenantUserService);
  }

  @Override
  protected String getRoleId() {
    return SERVICE_ROLE_ID;
  }

  @Override
  protected String getRoleName() {
    return SERVICE_ROLE_NAME;
  }

  @Override
  protected String getRoleDescription() {
    return SERVICE_ROLE_DESCRIPTION;
  }

  @Override
  protected Set<Capability> getRoleCapabilities() {
    return SERVICE_ROLE_CAPABILITIES;
  }

  @Override
  protected String getGroupId() {
    return SERVICE_GROUP_ID;
  }

  @Override
  protected String getGroupName() {
    return SERVICE_GROUP_NAME;
  }

  @Override
  protected String getGroupDescription() {
    return SERVICE_GROUP_DESCRIPTION;
  }

  @Transactional
  public void ensurePrivilegedUserExists(String tenantId) {
    Group group = createWellKnownGroupWithRole(createWellKnownRole(tenantId), tenantId);
    // UNIQUE by tenant
    String email = SERVICE_EMAIL_PATTERN.formatted(tenantId);

    Optional<User> existingEmailUser = userService.findByEmailIgnoreCase(email);

    if (existingEmailUser.isPresent()) {
      User user = existingEmailUser.get();
      // user.tokens collection is lazy, check in db.
      if (!userService.userHasToken(user.getId())) {
        // Email-matched user exists but has no token — reuse and attach token
        log.warn(
            "User with email {} already exists, but no token found. Reusing existing user.",
            user.getEmail());

        applyUserServiceAttributes(user, SERVICE_FIRSTNAME, null, email, group);

        userService.createUserToken(user);

        tenantUserService.attachToTenant(user.getId(), tenantId);
        userService.saveUser(user);
      }
    } else {
      // No user exists — create one
      User user =
          userService.createInternalUser(
              email, SERVICE_FIRSTNAME, null, false, UUID.randomUUID().toString());
      user.setGroups(new ArrayList<>(List.of(group)));
      tenantUserService.attachToTenant(user.getId(), tenantId);
      userService.saveUser(user);
    }
  }

  @Transactional(readOnly = true)
  public Optional<User> getUserServiceAccountByTenant(String tenantId) {
    String email = SERVICE_EMAIL_PATTERN.formatted(tenantId);

    return userService.findByEmailIgnoreCase(email).stream().findFirst();
  }

  @Transactional(readOnly = true)
  public String getTokenUserServiceAccountByTenant(String tenantId) {

    return getUserServiceAccountByTenant(tenantId)
        .map(User::getTokens)
        .filter(tokens -> !tokens.isEmpty())
        .map(tokens -> tokens.getFirst().getValue())
        .orElseThrow(() -> new UnsupportedOperationException("Token not found"));
  }
}

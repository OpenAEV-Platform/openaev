package io.openaev.api.users.dto;

import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import io.openaev.injector_contract.variables.contract.UserContract;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.Hibernate;
import org.springframework.util.ReflectionUtils;

public class UserMapper {

  /** Where a response view keeps the tenant its {@code user_organization} answers for. */
  private static final Field ORGANIZATION_TENANT_ID =
      Objects.requireNonNull(
          ReflectionUtils.findField(User.class, "organizationTenantId"),
          "User has no organizationTenantId field");

  private UserMapper() {}

  /**
   * A detached copy of the user for the response of a request that works on the given tenant: its
   * {@code user_organization} answers for that tenant instead of the ambient one, which is the
   * default tenant when the request names another in {@code X-Tenant-Ids}. Every field of the user
   * is copied, and the associations are shared with the user, not initialised by the copy. The only
   * final field of a user, its resource type, is a constant the copy already has. A Hibernate proxy
   * of the user is unwrapped first, which initialises the proxy: a proxy is a subclass of {@code
   * User} that the field copy refuses as a source. The copy is never persisted and the entity
   * itself is left as it is: a lifecycle event keeps it and serializes it later, for the consumers
   * of other tenants, which must not see the organization of this one.
   *
   * @param user the user the request worked on
   * @param tenantId the tenant the request works on
   */
  public static User toResponseView(User user, String tenantId) {
    User view = new User();
    ReflectionUtils.shallowCopyFieldState(Hibernate.unproxy(user), view);
    ReflectionUtils.makeAccessible(ORGANIZATION_TENANT_ID);
    ReflectionUtils.setField(ORGANIZATION_TENANT_ID, view, tenantId);
    return view;
  }

  /**
   * Maps a User entity to output without tenant information (tenant-scoped APIs).
   *
   * @param user the user to map
   * @param tenantId the tenant of the request, whose organization of the user is exposed
   * @param visibleOrganizations the organizations the caller may see, keyed by id
   */
  public static UserOutput toOutput(
      User user, String tenantId, Map<String, Organization> visibleOrganizations) {
    return toOutput(user, tenantId, visibleOrganizations, false);
  }

  /**
   * Maps a User entity to output with tenant information (platform-scoped APIs only).
   *
   * <p>Organizations are not handled at platform level: a user holds one organization per tenant,
   * so none is exposed here.
   *
   * @param user the user to map
   */
  public static UserOutput toPlatformOutput(User user) {
    return toOutput(user, null, Map.of(), true);
  }

  /**
   * Maps a UserContract class to User entity.
   *
   * @param userContract to map
   * @return mapped User
   */
  public static User fromUserContract(UserContract userContract) {
    User user = new User();
    user.setId(userContract.getId());
    user.setEmail(userContract.getEmail());
    user.setFirstname(userContract.getFirstname());
    user.setLastname(userContract.getLastname());
    user.setLang(userContract.getLang());
    user.setPgpKey(userContract.getPgpKey());
    user.setPhone(userContract.getPhone());
    return user;
  }

  private static UserOutput toOutput(
      User user,
      String tenantId,
      Map<String, Organization> visibleOrganizations,
      boolean includeTenants) {
    // A user is platform-level but holds one organization per tenant: only the request's tenant's
    // one is exposed, and only when the caller may see it. The platform output exposes none.
    Organization org = null;
    Organization ofTenant = includeTenants ? null : user.organizationOfTenant(tenantId);
    if (ofTenant != null) {
      org = visibleOrganizations.get(ofTenant.getId());
    }
    Set<String> tagIds =
        user.getTags() != null
            ? user.getTags().stream().map(Tag::getId).collect(Collectors.toSet())
            : Set.of();
    List<UserOutput.UserTenantOutput> tenantOutputs =
        includeTenants && user.getTenants() != null
            ? user.getTenants().stream()
                .map(t -> new UserOutput.UserTenantOutput(t.getId(), t.getName()))
                .toList()
            : null;
    return new UserOutput(
        user.getId(),
        user.getEmail(),
        user.getFirstname(),
        user.getLastname(),
        user.getPgpKey(),
        user.getPhone(),
        user.getPhone2(),
        org != null ? org.getId() : null,
        org != null ? org.getName() : null,
        tagIds,
        user.isAdmin(),
        tenantOutputs);
  }
}

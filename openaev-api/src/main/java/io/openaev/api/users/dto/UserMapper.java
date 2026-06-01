package io.openaev.api.users.dto;

import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UserMapper {

  private UserMapper() {}

  /** Maps a User entity to output without tenant information (tenant-scoped APIs). */
  public static UserOutput toOutput(User user) {
    return toOutput(user, false);
  }

  /** Maps a User entity to output with tenant information (platform-scoped APIs only). */
  public static UserOutput toPlatformOutput(User user) {
    return toOutput(user, true);
  }

  private static UserOutput toOutput(User user, boolean includeTenants) {
    Organization org = user.getOrganization();
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

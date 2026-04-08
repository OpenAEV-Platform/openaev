package io.openaev.api.users.dto;

import io.openaev.database.model.Organization;
import io.openaev.database.model.Tag;
import io.openaev.database.model.User;
import java.util.Set;
import java.util.stream.Collectors;

public class UserMapper {

  private UserMapper() {}

  public static UserOutput toOutput(User user) {
    Organization org = user.getOrganization();
    Set<String> tagIds =
        user.getTags() != null
            ? user.getTags().stream().map(Tag::getId).collect(Collectors.toSet())
            : Set.of();
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
        user.isAdmin());
  }
}

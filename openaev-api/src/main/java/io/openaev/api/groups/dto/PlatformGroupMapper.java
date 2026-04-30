package io.openaev.api.groups.dto;

import io.openaev.database.model.Group;

public class PlatformGroupMapper {

  private PlatformGroupMapper() {}

  public static PlatformGroupOutput toOutput(Group group) {
    return new PlatformGroupOutput(
        group.getId(), group.getName(), group.getDescription(), group.isDefaultUserAssignation());
  }
}

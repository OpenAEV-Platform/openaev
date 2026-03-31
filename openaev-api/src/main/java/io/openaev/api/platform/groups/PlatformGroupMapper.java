package io.openaev.api.platform.groups;

import io.openaev.database.model.PlatformGroup;

public class PlatformGroupMapper {

  private PlatformGroupMapper() {}

  public static PlatformGroupOutput toOutput(PlatformGroup group) {
    return new PlatformGroupOutput(group.getId(), group.getName(), group.getDescription());
  }
}

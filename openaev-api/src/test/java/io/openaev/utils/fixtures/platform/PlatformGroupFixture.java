package io.openaev.utils.fixtures.platform;

import io.openaev.database.model.PlatformGroup;

public class PlatformGroupFixture {

  public static final String PLATFORM_GROUP_NAME = "Platform Admins";
  public static final String PLATFORM_GROUP_DESCRIPTION = "Default platform admin group";

  public static PlatformGroup getPlatformGroup() {
    return getPlatformGroup(PLATFORM_GROUP_NAME);
  }

  public static PlatformGroup getPlatformGroup(String name) {
    PlatformGroup group = new PlatformGroup();
    group.setName(name);
    group.setDescription(PLATFORM_GROUP_DESCRIPTION);
    return group;
  }
}

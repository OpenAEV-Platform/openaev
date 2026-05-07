package io.openaev.utils.fixtures.platform;

import io.openaev.database.model.Group;

public class PlatformGroupFixture {

  public static final String PLATFORM_GROUP_NAME = "Platform Admins";
  public static final String PLATFORM_GROUP_DESCRIPTION = "Default platform admin group";

  public static Group getPlatformGroup(String name) {
    Group group = new Group();
    group.setName(name);
    group.setDescription(PLATFORM_GROUP_DESCRIPTION);
    group.setTenant(null);
    return group;
  }
}

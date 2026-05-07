package io.openaev.utils.fixtures;

import io.openaev.database.model.Group;

public class TenantGroupFixture {

  public static final String GROUP_NAME = "Tenant Group";
  public static final String GROUP_DESCRIPTION = "Default tenant group";

  public static Group getGroup() {
    return getGroup(GROUP_NAME);
  }

  public static Group getGroup(String name) {
    Group group = new Group();
    group.setName(name);
    group.setDescription(GROUP_DESCRIPTION);
    return group;
  }
}

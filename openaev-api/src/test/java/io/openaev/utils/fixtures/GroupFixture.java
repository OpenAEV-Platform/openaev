package io.openaev.utils.fixtures;

import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import java.util.List;

public class GroupFixture {

  public static Group createGroup() {
    return createGroupWithName("Group");
  }

  public static Group createGroupWithName(String name) {
    Group group = new Group();
    group.setName(name);
    group.setDescription("Group Description");
    return group;
  }

  public static Group createGroup(List<Role> roles, List<Grant> grants) {
    Group group = createGroup();
    group.setRoles(roles);
    group.setGrants(grants);
    return group;
  }
}

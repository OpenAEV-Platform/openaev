package io.openaev.database.model;

import java.util.List;

public interface GrantableBase extends Base {

  static Grant.GRANT_RESOURCE_TYPE getGrantResourceType(Class<?> clazz) {
    Grantable annotation = clazz.getAnnotation(Grantable.class);
    if (annotation == null) {
      throw new IllegalArgumentException(
          "Missing @GrantResource annotation on class: " + clazz.getName());
    }
    return annotation.value();
  }

  List<Grant> getGrants();

  void setGrants(List<Grant> grants);
}

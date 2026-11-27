package io.openaev.search;

import io.openaev.database.model.Base;
import io.openaev.database.model.Capability;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// nameAttribute is both searched and displayed as the result name
record SearchableClass<T extends Base>(
    Class<T> clazz,
    JpaSpecificationExecutor<T> repository,
    Capability capability,
    String nameAttribute,
    @Nullable String descriptionAttribute,
    Set<Class<? extends T>> exposedSubclasses) {

  SearchableClass(
      Class<T> clazz,
      JpaSpecificationExecutor<T> repository,
      Capability capability,
      String nameAttribute,
      @Nullable String descriptionAttribute) {
    this(clazz, repository, capability, nameAttribute, descriptionAttribute, Set.of());
  }

  static final String ID = "id";
  static final String TAGS = "tags";
  static final String NAME = "name";
  static final String DESCRIPTION = "description";
  static final String EMAIL = "email";

  List<String> searchAttributes() {
    return List.of(nameAttribute, ID);
  }

  // Exposed subclasses keep their own name in results, others take the searched class name
  String resultClass(Class<?> type) {
    return exposedSubclasses.contains(type) ? type.getSimpleName() : clazz.getSimpleName();
  }
}

package io.openaev.jsonapi;

import io.openaev.database.model.Grant;
import io.openaev.database.model.Group;
import io.openaev.database.model.Role;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.Token;
import io.openaev.database.model.User;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The resource types a JSON:API import may build: its root, and the resources it relates to. */
public record ImportTypes(Predicate<String> roots, Predicate<String> related) {

  /** A bundle describes content, never who may access it: no import builds nor binds these. */
  public static final Set<String> DENIED =
      Stream.of(User.class, Token.class, Grant.class, Group.class, Role.class, Tenant.class)
          .map(GenericJsonApiIUtils::resolveType)
          .collect(Collectors.toUnmodifiableSet());

  public static final ImportTypes ANY = new ImportTypes(type -> true, type -> true);

  public static ImportTypes only(Set<String> roots, Set<String> related) {
    return new ImportTypes(roots::contains, related::contains);
  }

  public boolean allowsRoot(String type) {
    return type != null && !DENIED.contains(type) && roots.test(type);
  }

  public boolean allowsRelated(String type) {
    return type != null && !DENIED.contains(type) && related.test(type);
  }
}

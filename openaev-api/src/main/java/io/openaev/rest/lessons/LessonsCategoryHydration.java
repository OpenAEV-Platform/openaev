package io.openaev.rest.lessons;

import io.openaev.database.model.LessonsCategory;
import java.util.List;
import org.hibernate.Hibernate;

/**
 * Hydrates, inside the scoped transaction, the lazy {@code lessons_category_teams} collection the
 * lessons responses serialize. The collection is loaded after the controller returns, through
 * open-in-view, where the tenant scope is already gone: a lazy load at that point runs unscoped and
 * fails closed, so the array comes back empty although the links exist.
 */
final class LessonsCategoryHydration {

  private LessonsCategoryHydration() {}

  static LessonsCategory hydrateForResponse(LessonsCategory category) {
    Hibernate.initialize(category.getTeams());
    return category;
  }

  static List<LessonsCategory> hydrateForResponse(Iterable<LessonsCategory> categories) {
    List<LessonsCategory> hydrated = new java.util.ArrayList<>();
    categories.forEach(category -> hydrated.add(hydrateForResponse(category)));
    return hydrated;
  }
}

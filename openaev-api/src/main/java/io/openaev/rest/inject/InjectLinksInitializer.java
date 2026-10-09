package io.openaev.rest.inject;

import io.openaev.database.model.Inject;
import java.util.Collection;
import org.hibernate.Hibernate;

/**
 * Forces the LAZY {@code Inject#tags} and {@code Inject#secretReferences} associations to load
 * while the tenant scope is still set. The inject read endpoints return the raw entity, serialized
 * open-in-view after the commit, when a lazy load on these v2-active tables fails closed to an
 * empty array: the inject edit form, filled from that response, then erased the inject's tags.
 */
public final class InjectLinksInitializer {

  private InjectLinksInitializer() {}

  public static void initialize(Collection<Inject> injects) {
    injects.forEach(
        inject -> {
          Hibernate.initialize(inject.getTags());
          Hibernate.initialize(inject.getSecretReferences());
        });
  }
}

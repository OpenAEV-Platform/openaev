package io.openaev.rest.inject;

import io.openaev.database.model.Inject;
import java.util.Collection;
import org.hibernate.Hibernate;

/**
 * Loads the LAZY {@code Inject#tags} and {@code Inject#secretReferences} associations inside the
 * request's scoped transaction. An endpoint that returns the raw entity is serialized open-in-view
 * after the commit, where a lazy load of these isolated tables reads zero rows, so the response
 * would carry an empty array.
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

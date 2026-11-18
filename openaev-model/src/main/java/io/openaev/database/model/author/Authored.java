package io.openaev.database.model.author;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.openaev.database.model.Organization;
import io.openaev.database.model.User;

/**
 * Content with an {@link Authorship}. The author stays local to the platform and is never exported.
 * Hibernate loads an authorship whose columns are all empty as {@code null}, so the accessors below
 * read through it safely.
 */
public interface Authored {

  Authorship getAuthor();

  void setAuthor(Authorship author);

  @JsonIgnore
  default User getAuthorUser() {
    return getAuthor() == null ? null : getAuthor().getUser();
  }

  default void setAuthorUser(User user) {
    authorship().setUser(user);
  }

  @JsonIgnore
  default Organization getAuthorOrganization() {
    return getAuthor() == null ? null : getAuthor().getOrganization();
  }

  default void setAuthorOrganization(Organization organization) {
    authorship().setOrganization(organization);
  }

  private Authorship authorship() {
    if (getAuthor() == null) {
      setAuthor(new Authorship());
    }
    return getAuthor();
  }
}

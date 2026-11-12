package io.openaev.database.model.author;

import io.openaev.database.model.Organization;
import io.openaev.database.model.User;
import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.Setter;

/** Who authored a piece of content: a user or an organization, never both. */
@Getter
@Setter
@Embeddable
public class Authorship {

  @ManyToOne(fetch = FetchType.LAZY)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  private Organization organization;

  public Authorship copy() {
    Authorship copy = new Authorship();
    copy.setUser(user);
    copy.setOrganization(organization);
    return copy;
  }
}

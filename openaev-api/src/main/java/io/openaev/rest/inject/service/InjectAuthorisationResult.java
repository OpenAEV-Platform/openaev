package io.openaev.rest.inject.service;

import io.openaev.database.model.Inject;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class InjectAuthorisationResult {
  private List<Inject> authorised = new ArrayList<>();
  private List<Inject> unauthorised = new ArrayList<>();

  public void addAuthorised(Inject inject) {
    authorised.add(inject);
  }

  public void addUnauthorised(Inject inject) {
    unauthorised.add(inject);
  }
}

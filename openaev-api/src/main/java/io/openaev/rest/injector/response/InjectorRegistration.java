package io.openaev.rest.injector.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InjectorRegistration {

  @JsonProperty("connection")
  private InjectorConnection connection;

  @JsonProperty("listen")
  private String listen;

  public InjectorRegistration(InjectorConnection connection, String listen) {
    this.connection = connection;
    this.listen = listen;
  }
}

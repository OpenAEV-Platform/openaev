package io.openaev.rest.injector.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.service.BrokerConnectionInfo;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InjectorRegistration {

  @JsonProperty("connection")
  private BrokerConnectionInfo connection;

  @JsonProperty("listen")
  private String listen;

  public InjectorRegistration(BrokerConnectionInfo connection, String listen) {
    this.connection = connection;
    this.listen = listen;
  }
}

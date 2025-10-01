package io.openaev.rest.injector.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InjectorConnection {

  @JsonProperty("host")
  private String host;

  @JsonProperty("vhost")
  private String vhost;

  @JsonProperty("use_ssl")
  private boolean useSsl;

  @JsonProperty("port")
  private int port;

  @JsonProperty("user")
  private String user;

  @JsonProperty("pass")
  private String pass;

  public InjectorConnection(
      String host, String vhost, boolean useSsl, int port, String user, String pass) {
    this.host = host;
    this.vhost = vhost;
    this.useSsl = useSsl;
    this.port = port;
    this.user = user;
    this.pass = pass;
  }
}

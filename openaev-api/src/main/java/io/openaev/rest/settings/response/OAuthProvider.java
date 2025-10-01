package io.openaev.rest.settings.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OAuthProvider {

  @JsonProperty("provider_name")
  private String name;

  @JsonProperty("provider_uri")
  private String uri;

  @JsonProperty("provider_login")
  private String login;

  public OAuthProvider(String name, String uri, String login) {
    this.name = name;
    this.uri = uri;
    this.login = login;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLogin() {
    return login;
  }

  public void setLogin(String login) {
    this.login = login;
  }

  public String getUri() {
    return uri;
  }

  public void setUri(String uri) {
    this.uri = uri;
  }
}

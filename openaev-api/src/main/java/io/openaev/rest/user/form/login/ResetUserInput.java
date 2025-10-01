package io.openaev.rest.user.form.login;

import static io.openaev.config.AppConfig.MANDATORY_MESSAGE;

import jakarta.validation.constraints.NotBlank;

public class ResetUserInput {

  @NotBlank(message = MANDATORY_MESSAGE)
  private String login;

  private String lang;

  public String getLogin() {
    return login;
  }

  public void setLogin(String login) {
    this.login = login;
  }

  public String getLang() {
    return lang;
  }

  public void setLang(String lang) {
    this.lang = lang;
  }
}

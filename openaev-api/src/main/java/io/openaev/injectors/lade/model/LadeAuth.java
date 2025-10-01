package io.openaev.injectors.lade.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class LadeAuth {

  public enum TokenStatus {
    empty,
    set,
    expire,
  }

  private String token;

  private Instant validity;

  public TokenStatus getTokenStatus(Integer sessionTime) {
    if (token == null || validity == null) {
      return TokenStatus.empty;
    }
    int maxBeforeRefresh = sessionTime / 2;
    long sinceExpire = ChronoUnit.MINUTES.between(validity, Instant.now());
    if (sinceExpire > maxBeforeRefresh) {
      return TokenStatus.expire;
    }
    return TokenStatus.set;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
    this.validity = Instant.now();
  }

  public void refreshValidity() {
    this.validity = Instant.now();
  }

  public void clear() {
    this.token = null;
    this.validity = null;
  }
}

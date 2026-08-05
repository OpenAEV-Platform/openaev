package io.openaev.config;

public enum RunMode {
  NORMAL("normal"),
  SAFE("safe");

  private final String value;

  RunMode(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}

package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonValue;

/** Startup run mode controlling whether Quartz background processing starts. */
public enum RunMode {
  NORMAL("normal"),
  SAFE("safe");

  private final String value;

  RunMode(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }
}

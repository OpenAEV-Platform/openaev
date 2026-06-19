package io.openaev.migration;

import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ClassNameGenerator {
  private final String majorVersionPrefix = "V6";

  private String getPrefix() {
    return majorVersionPrefix;
  }

  private String getCurrentUTCTimestamp() {
    return DateTimeFormatter.ofPattern("yMMddHHmmssSSS")
        .format(LocalDateTime.now(ZoneId.of("UTC")));
  }

  public String generate(String className) {
    return MessageFormat.format("{0}_{1}__{2}", getPrefix(), getCurrentUTCTimestamp(), className);
  }
}

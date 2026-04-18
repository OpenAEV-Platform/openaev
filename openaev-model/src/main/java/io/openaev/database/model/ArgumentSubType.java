package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;

/**
 * Enumeration of the subtypes of an ArgumentType
 *
 * <p>The subtype is <em>optional</em>: simple scalar types ({@link ArgumentType#Text}, {@link
 * ArgumentType#Number}, {@link ArgumentType#Port}, {@link ArgumentType#IPv4}, {@link
 * ArgumentType#IPv6}) have no fields in their processors and therefore never need a subtype.
 */
public enum ArgumentSubType {
  @JsonProperty("host")
  Host("host"),

  @JsonProperty("port")
  Port("port"),

  @JsonProperty("service")
  Service("service"),

  @JsonProperty("username")
  Username("username"),

  @JsonProperty("password")
  Password("password"),

  @JsonProperty("severity")
  Severity("severity"),

  @JsonProperty("domain")
  Domain("domain");

  public final String label;

  ArgumentSubType(String label) {
    this.label = label;
  }

  /**
   * Looks up an {@link ArgumentSubType} by its JSON label. Used for manual JSON parsing (e.g.
   * {@code PayloadUtils.buildPayload}). Jackson uses the {@code @JsonProperty} annotations on the
   * constants directly.
   *
   * @param label the raw string value
   * @return the matching enum constant
   * @throws IllegalArgumentException when no constant matches
   */
  public static ArgumentSubType fromLabel(String label) {
    return Arrays.stream(values())
        .filter(v -> v.label.equals(label))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Unknown ArgumentSubType label: '"
                        + label
                        + "'. Valid values: "
                        + Arrays.toString(values())));
  }
}

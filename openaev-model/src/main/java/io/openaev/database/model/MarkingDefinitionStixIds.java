package io.openaev.database.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The STIX {@code standard_id} of a marking definition, derived exactly as OpenCTI derives it, so a
 * marking is the same object in OpenCTI, OpenAEV, OpenCRQ and XTM One.
 *
 * <p>The five TLP 2.0 markings carry the static ids OpenCTI ships (TLP:WHITE is an alias of
 * TLP:CLEAR). Every other marking gets a UUIDv5 in OpenCTI's OASIS namespace of the canonical JSON
 * (RFC 8785) of its upper-cased {@code definition} and {@code definition_type}, as OpenCTI's {@code
 * schema/identifier.js} and pycti's {@code MarkingDefinition.generate_id} do.
 */
public final class MarkingDefinitionStixIds {

  public static final String PREFIX = "marking-definition--";

  /** OpenCTI's OASIS_NAMESPACE (opencti-graphql src/schema/general.js). */
  static final UUID OASIS_NAMESPACE = UUID.fromString("00abedb4-aa42-466c-9c01-fed23315a9b7");

  private static final Map<String, String> STATIC_TLP_IDS =
      Map.of(
          "TLP:WHITE", PREFIX + "613f2e26-407d-48c7-9eca-b8e91df99dc9",
          "TLP:CLEAR", PREFIX + "613f2e26-407d-48c7-9eca-b8e91df99dc9",
          "TLP:GREEN", PREFIX + "34098fce-860f-48ae-8e50-ebd3cc5e41da",
          "TLP:AMBER", PREFIX + "f88d31f6-486f-44da-b317-01333bde0b82",
          "TLP:AMBER+STRICT", PREFIX + "826578e1-40ad-459f-bc73-ede076f81f37",
          "TLP:RED", PREFIX + "5e57c739-391a-4eb3-b6be-7d15ca92d5ed");

  private static final Pattern STIX_ID =
      Pattern.compile(
          "^marking-definition--[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

  private MarkingDefinitionStixIds() {}

  /**
   * The STIX id OpenCTI gives a marking of this type and definition.
   *
   * @param type the marking type (TLP, PAP, STATEMENT...)
   * @param definition the marking definition (TLP:AMBER, PAP:RED...)
   * @return {@code marking-definition--<uuid>}
   */
  public static String generate(String type, String definition) {
    String upperType = upper(type);
    String upperDefinition = upper(definition);
    String staticId = staticTlpId(upperType, upperDefinition);
    if (staticId != null) {
      return staticId;
    }
    // RFC 8785 canonical form of {definition, definition_type}: keys sorted, no whitespace.
    String canonical =
        "{\"definition\":"
            + quote(upperDefinition)
            + ",\"definition_type\":"
            + quote(upperType)
            + "}";
    return PREFIX + uuidV5(OASIS_NAMESPACE, canonical);
  }

  /**
   * The static id OpenCTI fixes for a TLP 2.0 marking, or {@code null} for any other marking.
   *
   * @param type the marking type
   * @param definition the marking definition
   * @return the static id, or {@code null}
   */
  public static String staticTlpId(String type, String definition) {
    if (!"TLP".equals(upper(type))) {
      return null;
    }
    return STATIC_TLP_IDS.get(upper(definition));
  }

  /**
   * Whether the value is a well-formed STIX marking-definition id.
   *
   * @param stixId the value to check
   * @return {@code true} when it reads {@code marking-definition--<lower-case uuid>}
   */
  public static boolean isValid(String stixId) {
    return stixId != null && STIX_ID.matcher(stixId).matches();
  }

  private static String upper(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  /** A JSON string literal as RFC 8785 serializes it. */
  static String quote(String value) {
    StringBuilder out = new StringBuilder(value.length() + 2).append('"');
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }

  static UUID uuidV5(UUID namespace, String name) {
    MessageDigest sha1;
    try {
      sha1 = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    ByteBuffer ns = ByteBuffer.allocate(16);
    ns.putLong(namespace.getMostSignificantBits());
    ns.putLong(namespace.getLeastSignificantBits());
    sha1.update(ns.array());
    byte[] hash = sha1.digest(name.getBytes(StandardCharsets.UTF_8));
    hash[6] = (byte) ((hash[6] & 0x0f) | 0x50);
    hash[8] = (byte) ((hash[8] & 0x3f) | 0x80);
    ByteBuffer bytes = ByteBuffer.wrap(hash, 0, 16);
    return new UUID(bytes.getLong(), bytes.getLong());
  }
}

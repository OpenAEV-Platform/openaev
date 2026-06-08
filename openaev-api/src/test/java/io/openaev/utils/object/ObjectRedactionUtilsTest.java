package io.openaev.utils.object;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ObjectRedactionUtils")
class ObjectRedactionUtilsTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String REDACTED_TEXT = "*** Redacted ***";

  @Nested
  @DisplayName("redact")
  class Redact {

    @Test
    void given_nullNode_should_returnNull() {
      // Arrange
      JsonNode node = null;

      // Act
      JsonNode result = ObjectRedactionUtils.redact(node, ResourceType.USER);

      // Assert
      assertThat(result).isNull();
    }

    @Test
    void given_userEntity_should_removeUserPiiFields() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "John Doe");
      node.put("user_email", "john@openaev.io");
      node.put("user_phone", "123456");
      node.put("safe_field", "safe");

      // Act
      JsonNode result = ObjectRedactionUtils.redact(node, ResourceType.USER);

      // Assert
      assertThat(result.has("name")).isFalse();
      assertThat(result.has("user_email")).isFalse();
      assertThat(result.has("user_phone")).isFalse();
      assertThat(result.path("safe_field").asText()).isEqualTo("safe");
    }

    @Test
    void given_nonUserEntity_should_keepUserPiiFields() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "John Doe");
      node.put("user_email", "john@openaev.io");

      // Act
      JsonNode result = ObjectRedactionUtils.redact(node, ResourceType.TEAM);

      // Assert
      assertThat(result.path("name").asText()).isEqualTo("John Doe");
      assertThat(result.path("user_email").asText()).isEqualTo("john@openaev.io");
    }

    @Test
    void given_sensitiveRegexFields_should_redactValues() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("db_password", "secretValue");
      node.put("client_secret", "secretValue");
      node.put("smtp_credential", "secretValue");

      // Act
      JsonNode result = ObjectRedactionUtils.redact(node, ResourceType.TEAM);

      // Assert
      assertThat(result.path("db_password").asText()).isEqualTo(REDACTED_TEXT);
      assertThat(result.path("client_secret").asText()).isEqualTo(REDACTED_TEXT);
      assertThat(result.path("smtp_credential").asText()).isEqualTo(REDACTED_TEXT);
    }

    @Test
    void given_allowedSensitiveSuffixes_should_notRedact() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("password_date", "2026-01-01");
      node.put("secret_time", "12:00:00");
      node.put("credential_at", "2026-01-01T12:00:00Z");

      // Act
      JsonNode result = ObjectRedactionUtils.redact(node, ResourceType.TEAM);

      // Assert
      assertThat(result.path("password_date").asText()).isEqualTo("2026-01-01");
      assertThat(result.path("secret_time").asText()).isEqualTo("12:00:00");
      assertThat(result.path("credential_at").asText()).isEqualTo("2026-01-01T12:00:00Z");
    }

    @Test
    void given_hashRegexFields_should_hashValues() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("token", "plain-token");
      node.put("apikey", "plain-apikey");
      node.put("api_key", "plain-api-key");
      node.put("user_pgp_key", "plain-pgp");
      node.put("endpoint_mac_addresses", "AA:BB:CC");

      // Act
      JsonNode result = ObjectRedactionUtils.redact(node, ResourceType.TEAM);

      // Assert
      assertThat(result.path("token").asText()).isEqualTo(hashWithSHA256("plain-token"));
      assertThat(result.path("apikey").asText()).isEqualTo(hashWithSHA256("plain-apikey"));
      assertThat(result.path("api_key").asText()).isEqualTo(hashWithSHA256("plain-api-key"));
      assertThat(result.path("user_pgp_key").asText()).isEqualTo(hashWithSHA256("plain-pgp"));
      assertThat(result.path("endpoint_mac_addresses").asText())
          .isEqualTo(hashWithSHA256("AA:BB:CC"));
    }

    @Test
    void given_hashAllowedSuffixes_should_notHash() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("token_date", "2026-01-01");
      node.put("apikey_time", "12:00:00");
      node.put("api_key_at", "2026-01-01T12:00:00Z");

      // Act
      JsonNode result = ObjectRedactionUtils.redact(node, ResourceType.TEAM);

      // Assert
      assertThat(result.path("token_date").asText()).isEqualTo("2026-01-01");
      assertThat(result.path("apikey_time").asText()).isEqualTo("12:00:00");
      assertThat(result.path("api_key_at").asText()).isEqualTo("2026-01-01T12:00:00Z");
    }

    @Test
    void given_nestedObjectAndArray_should_processRecursively() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      ObjectNode nested = OBJECT_MAPPER.createObjectNode();
      nested.put("password", "nested-pass");
      nested.put("token", "nested-token");
      node.set("nested", nested);

      node.putArray("items")
          .add(OBJECT_MAPPER.createObjectNode().put("client_secret", "arr-secret"))
          .add(OBJECT_MAPPER.createObjectNode().put("api_key", "arr-api"));

      // Act
      JsonNode result = ObjectRedactionUtils.redact(node, ResourceType.TEAM);

      // Assert
      assertThat(result.path("nested").path("password").asText()).isEqualTo(REDACTED_TEXT);
      assertThat(result.path("nested").path("token").asText())
          .isEqualTo(hashWithSHA256("nested-token"));
      assertThat(result.path("items").get(0).path("client_secret").asText())
          .isEqualTo(REDACTED_TEXT);
      assertThat(result.path("items").get(1).path("api_key").asText())
          .isEqualTo(hashWithSHA256("arr-api"));
    }
  }

  @Nested
  @DisplayName("redactFieldValue")
  class RedactFieldValue {

    @Test
    void given_nullValue_should_returnNull() {
      // Arrange

      // Act
      Object result = ObjectRedactionUtils.redactFieldValue(null, "token");

      // Assert
      assertThat(result).isNull();
    }

    @Test
    void given_userPiiFieldName_should_returnNull() {
      // Arrange

      // Act
      Object result = ObjectRedactionUtils.redactFieldValue("john@openaev.io", "user_email");

      // Assert
      assertThat(result).isNull();
    }

    @Test
    void given_hashFieldName_should_returnHashedString() {
      // Arrange

      // Act
      Object result = ObjectRedactionUtils.redactFieldValue("plain-token", "token");

      // Assert
      assertThat(result).isEqualTo(hashWithSHA256("plain-token"));
    }

    @Test
    void given_redactFieldName_should_returnRedactedMarker() {
      // Arrange

      // Act
      Object result = ObjectRedactionUtils.redactFieldValue("plain-password", "password");

      // Assert
      assertThat(result).isEqualTo(REDACTED_TEXT);
    }

    @Test
    void given_blankString_should_returnOriginalValue() {
      // Arrange

      // Act
      Object result = ObjectRedactionUtils.redactFieldValue("   ", "password");

      // Assert
      assertThat(result).isEqualTo("   ");
    }
  }
}

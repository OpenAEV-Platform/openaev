package io.openaev.utils.object;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.database.model.ResourceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Object redaction tests")
class ObjectRedactionUtilsTest {

  private static final String SENSITIVE_VALUE = "__SENSITIVE_VALUE__";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * The redaction marker is private, so it is read back from a field known to be redacted instead
   * of being duplicated here.
   */
  private static String redactionMarker() {
    return (String) ObjectRedactionUtils.redactFieldValue(SENSITIVE_VALUE, "aws_secret_access_key");
  }

  @Nested
  @DisplayName("redactFieldValue")
  class RedactFieldValue {

    @Test
    @DisplayName("given_azureClientSecret_should_beRedacted")
    void given_azureClientSecret_should_beRedacted() {
      // Act
      Object redacted =
          ObjectRedactionUtils.redactFieldValue(SENSITIVE_VALUE, "azure_client_secret");

      // Assert
      assertThat(redacted).isEqualTo(redactionMarker());
    }

    @Test
    @DisplayName("given_azureTenantId_should_beRedacted")
    void given_azureTenantId_should_beRedacted() {
      // Act
      Object redacted = ObjectRedactionUtils.redactFieldValue(SENSITIVE_VALUE, "azure_tenant_id");

      // Assert: stored in clear text, so only the redaction protects the audit log
      assertThat(redacted).isEqualTo(redactionMarker());
    }

    @Test
    @DisplayName("given_azureSubscriptionId_should_beRedacted")
    void given_azureSubscriptionId_should_beRedacted() {
      // Act
      Object redacted =
          ObjectRedactionUtils.redactFieldValue(SENSITIVE_VALUE, "azure_subscription_id");

      // Assert
      assertThat(redacted).isEqualTo(redactionMarker());
    }

    @Test
    @DisplayName("given_azureNonSensitiveFields_should_beKept")
    void given_azureNonSensitiveFields_should_beKept() {
      // Act & Assert: those two are needed to prefill the edit form
      assertThat(ObjectRedactionUtils.redactFieldValue("AzureCloud", "azure_environment"))
          .isEqualTo("AzureCloud");
      assertThat(ObjectRedactionUtils.redactFieldValue("a-client-id", "azure_client_id"))
          .isEqualTo("a-client-id");
    }

    @Test
    @DisplayName("given_nullValue_should_returnNull")
    void given_nullValue_should_returnNull() {
      // Act & Assert
      assertThat(ObjectRedactionUtils.redactFieldValue(null, "azure_tenant_id")).isNull();
    }
  }

  @Nested
  @DisplayName("redact")
  class Redact {

    @Test
    @DisplayName("given_azureCredentialPayload_should_redactSensitiveFieldsOnly")
    void given_azureCredentialPayload_should_redactSensitiveFieldsOnly() {
      // Arrange
      ObjectNode payload = MAPPER.createObjectNode();
      payload.put("azure_environment", "AzureCloud");
      payload.put("azure_client_id", "a-client-id");
      payload.put("azure_client_secret", SENSITIVE_VALUE);
      payload.put("azure_tenant_id", SENSITIVE_VALUE);
      payload.put("azure_subscription_id", SENSITIVE_VALUE);

      // Act
      JsonNode redacted = ObjectRedactionUtils.redact(payload, ResourceType.CREDENTIAL);

      // Assert
      assertThat(redacted.toString()).doesNotContain(SENSITIVE_VALUE);
      assertThat(redacted.get("azure_environment").asText()).isEqualTo("AzureCloud");
      assertThat(redacted.get("azure_client_id").asText()).isEqualTo("a-client-id");
    }

    @Test
    @DisplayName("given_payload_should_notModifyTheOriginalNode")
    void given_payload_should_notModifyTheOriginalNode() {
      // Arrange
      ObjectNode payload = MAPPER.createObjectNode();
      payload.put("azure_tenant_id", SENSITIVE_VALUE);

      // Act
      ObjectRedactionUtils.redact(payload, ResourceType.CREDENTIAL);

      // Assert: redaction works on a deep copy
      assertThat(payload.get("azure_tenant_id").asText()).isEqualTo(SENSITIVE_VALUE);
    }
  }
}

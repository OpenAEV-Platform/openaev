package io.openaev.aop.audit_log;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.audit.AuditLogHash;
import io.openaev.database.audit.AuditLogIgnore;
import io.openaev.database.audit.AuditLogRedact;
import org.junit.jupiter.api.Test;

class AuditObjectMapperTest {

  @Test
  void given_auditIgnoredField_should_excludeItFromAuditPayload_only() {
    // Arrange
    ObjectMapper httpMapper = new ObjectMapper();
    AuditObjectMapper auditMapper = new AuditObjectMapper(httpMapper);
    SamplePayload payload = new SamplePayload("id-1", "visible", "secret");

    // Act
    var httpJson = httpMapper.valueToTree(payload);
    var auditJson = auditMapper.valueToTree(payload);

    // Assert
    assertThat(httpJson.path("secret_value").isMissingNode()).isFalse();
    assertThat(auditJson.path("secret_value").isMissingNode()).isTrue();
    assertThat(auditJson.path("public_value").asText()).isEqualTo("visible");
  }

  @Test
  void given_auditHashAndRedactFields_should_maskOnlyInAuditPayload() {
    // Arrange
    ObjectMapper httpMapper = new ObjectMapper();
    AuditObjectMapper auditMapper = new AuditObjectMapper(httpMapper);
    SampleMaskedPayload payload = new SampleMaskedPayload("id-2", "api-token", "plain-secret");

    // Act
    var httpJson = httpMapper.valueToTree(payload);
    var auditJson = auditMapper.valueToTree(payload);

    // Assert
    assertThat(httpJson.path("token_value").asText()).isEqualTo("api-token");
    assertThat(httpJson.path("secret_value").asText()).isEqualTo("plain-secret");
    assertThat(auditJson.path("token_value").asText()).isEqualTo(hashWithSHA256("api-token"));
    assertThat(auditJson.path("secret_value").asText()).isEqualTo("[REDACTED]");
  }

  @Test
  void given_auditRemovedField_should_hideItFromAuditPayload_only() {
    // Arrange
    ObjectMapper httpMapper = new ObjectMapper();
    AuditObjectMapper auditMapper = new AuditObjectMapper(httpMapper);
    SampleRemovedPayload payload = new SampleRemovedPayload("id-3", "john@company.tld", "visible");

    // Act
    var httpJson = httpMapper.valueToTree(payload);
    var auditJson = auditMapper.valueToTree(payload);

    // Assert
    assertThat(httpJson.path("user_email").asText()).isEqualTo("john@company.tld");
    assertThat(auditJson.path("user_email").isMissingNode()).isTrue();
    assertThat(auditJson.path("public_value").asText()).isEqualTo("visible");
  }

  private record SamplePayload(
      @JsonProperty("payload_id") String id,
      @JsonProperty("public_value") String publicValue,
      @AuditLogIgnore @JsonProperty("secret_value") String secretValue) {}

  private record SampleMaskedPayload(
      @JsonProperty("payload_id") String id,
      @AuditLogHash @JsonProperty("token_value") String tokenValue,
      @AuditLogRedact @JsonProperty("secret_value") String secretValue) {}

  private record SampleRemovedPayload(
      @JsonProperty("payload_id") String id,
      @AuditLogIgnore @JsonProperty("user_email") String email,
      @JsonProperty("public_value") String publicValue) {}
}

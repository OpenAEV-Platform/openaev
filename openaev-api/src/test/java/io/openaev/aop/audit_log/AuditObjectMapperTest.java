package io.openaev.aop.audit_log;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.audit.AuditLogIgnore;
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

  private record SamplePayload(
      @JsonProperty("payload_id") String id,
      @JsonProperty("public_value") String publicValue,
      @AuditLogIgnore @JsonProperty("secret_value") String secretValue) {}
}

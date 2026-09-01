package io.openaev.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("AuditLogFilenameBasePropertyDefiner")
class AuditLogFilenameBasePropertyDefinerTest {

  private static final String AUDIT_LOG_DIR = "logs";
  private static final String FIXED_DATE = "2026-08-28";
  private static final String FIXED_INDEX = "0";

  @Nested
  @DisplayName("filename normalization and rotation naming")
  class FilenameNormalizationAndRotationNaming {

    @Test
    @DisplayName("given_propertiesAuditDotLog_should_normalizeBase_and_keepSingleFinalLogExtension")
    void given_propertiesAuditDotLog_should_normalizeBase_and_keepSingleFinalLogExtension() {
      // Arrange
      AuditLogFilenameBasePropertyDefiner definer = new AuditLogFilenameBasePropertyDefiner();
      definer.setFromProperties("audit.log");

      // Act
      String baseFilename = definer.getPropertyValue();
      String rotationFilename = buildRotationFilename(baseFilename);

      // Assert
      assertThat(baseFilename).isEqualTo("audit");
      assertThat(rotationFilename).isEqualTo("logs/audit.2026-08-28.0.log");
    }

    @Test
    @DisplayName(
        "given_envVariableAuditDotLog_should_normalizeBase_and_keepSingleFinalLogExtension")
    void given_envVariableAuditDotLog_should_normalizeBase_and_keepSingleFinalLogExtension() {
      // Arrange
      AuditLogFilenameBasePropertyDefiner definer = new AuditLogFilenameBasePropertyDefiner();
      definer.setFromEnvVariable("audit.log");

      // Act
      String baseFilename = definer.getPropertyValue();
      String rotationFilename = buildRotationFilename(baseFilename);

      // Assert
      assertThat(baseFilename).isEqualTo("audit");
      assertThat(rotationFilename).isEqualTo("logs/audit.2026-08-28.0.log");
    }

    @Test
    @DisplayName("given_bothSourcesSet_should_prioritizeEnvVariable_forRotationFilename")
    void given_bothSourcesSet_should_prioritizeEnvVariable_forRotationFilename() {
      // Arrange
      AuditLogFilenameBasePropertyDefiner definer = new AuditLogFilenameBasePropertyDefiner();
      definer.setFromProperties("security-audit");
      definer.setFromEnvVariable("legacy-audit.log");

      // Act
      String baseFilename = definer.getPropertyValue();
      String rotationFilename = buildRotationFilename(baseFilename);

      // Assert
      assertThat(baseFilename).isEqualTo("legacy-audit");
      assertThat(rotationFilename).isEqualTo("logs/legacy-audit.2026-08-28.0.log");
    }
  }

  private String buildRotationFilename(String baseFilename) {
    return AUDIT_LOG_DIR + "/" + baseFilename + "." + FIXED_DATE + "." + FIXED_INDEX + ".log";
  }
}

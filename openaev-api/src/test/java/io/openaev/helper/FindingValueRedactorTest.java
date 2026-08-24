package io.openaev.helper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openaev.database.model.Finding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Finding value redaction")
class FindingValueRedactorTest {

  @Nested
  @DisplayName("When the finding is not sensitive")
  class WhenNotSensitive {

    @Test
    @DisplayName("Should return the value untouched")
    void given_aNonSensitiveFinding_should_returnTheValueAsIs() {
      // -------- Act --------
      String redacted = FindingValueRedactor.redact("admin:Sup3rS3cret", false);

      // -------- Assert --------
      assertThat(redacted).isEqualTo("admin:Sup3rS3cret");
    }
  }

  @Nested
  @DisplayName("When the finding is sensitive")
  class WhenSensitive {

    @Test
    @DisplayName("Should keep the identity part and mask the secret part")
    void given_aCredentialShapedValue_should_maskOnlyTheSecretPart() {
      // -------- Act --------
      String redacted = FindingValueRedactor.redact("admin:Sup3rS3cret", true);

      // -------- Assert --------
      assertThat(redacted).isEqualTo("admin:" + FindingValueRedactor.MASK);
      assertThat(redacted).doesNotContain("Sup3rS3cret");
    }

    @Test
    @DisplayName("Should mask the secret part of a value holding several separators")
    void given_aValueWithSeveralSeparators_should_maskEverythingAfterTheFirstOne() {
      // -------- Act --------
      String redacted = FindingValueRedactor.redact("admin:aad3b435:31d6cfe0", true);

      // -------- Assert --------
      assertThat(redacted).isEqualTo("admin:" + FindingValueRedactor.MASK);
    }

    @Test
    @DisplayName("Should keep a two character fragment of a value without identity part")
    void given_aValueWithoutSeparator_should_keepAFragment() {
      // -------- Act --------
      String redacted = FindingValueRedactor.redact("Sup3rS3cret", true);

      // -------- Assert --------
      assertThat(redacted).isEqualTo("Su" + FindingValueRedactor.MASK);
    }

    @Test
    @DisplayName("Should mask a short value entirely")
    void given_aShortValue_should_maskItEntirely() {
      // -------- Act --------
      String redacted = FindingValueRedactor.redact("abcd", true);

      // -------- Assert --------
      assertThat(redacted).isEqualTo(FindingValueRedactor.MASK);
    }

    @Test
    @DisplayName("Should mask a value whose secret part is empty as a whole")
    void given_aValueEndingWithTheSeparator_should_maskItWithAFragment() {
      // -------- Act --------
      String redacted = FindingValueRedactor.redact("administrator:", true);

      // -------- Assert --------
      assertThat(redacted).isEqualTo("ad" + FindingValueRedactor.MASK);
    }

    @Test
    @DisplayName("Should return blank and null values as is")
    void given_aBlankValue_should_returnItAsIs() {
      // -------- Act & Assert --------
      assertThat(FindingValueRedactor.redact(null, true)).isNull();
      assertThat(FindingValueRedactor.redact("  ", true)).isEqualTo("  ");
    }
  }

  @Nested
  @DisplayName("When serializing a finding")
  class WhenSerializing {

    @Test
    @DisplayName("Should redact the value of a sensitive finding only")
    void given_findings_should_redactOnlyTheSensitiveOne() throws Exception {
      // -------- Arrange --------
      Finding sensitiveFinding = new Finding();
      sensitiveFinding.setValue("admin:Sup3rS3cret");
      sensitiveFinding.setSensitive(true);

      Finding regularFinding = new Finding();
      regularFinding.setValue("admin:Sup3rS3cret");

      ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

      // -------- Act --------
      String sensitiveJson = mapper.writeValueAsString(sensitiveFinding);
      String regularJson = mapper.writeValueAsString(regularFinding);

      // -------- Assert --------
      assertThat(sensitiveJson).contains("\"finding_value\":\"admin:" + FindingValueRedactor.MASK);
      assertThat(sensitiveJson).contains("\"finding_is_sensitive\":true");
      assertThat(sensitiveJson).doesNotContain("Sup3rS3cret");
      assertThat(regularJson).contains("\"finding_value\":\"admin:Sup3rS3cret\"");
      // The entity itself keeps the cleartext value: only its serialized form is redacted.
      assertThat(sensitiveFinding.getValue()).isEqualTo("admin:Sup3rS3cret");
    }
  }
}

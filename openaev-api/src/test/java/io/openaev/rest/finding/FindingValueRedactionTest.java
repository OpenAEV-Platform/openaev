package io.openaev.rest.finding;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Finding value redaction")
class FindingValueRedactionTest {

  @Nested
  @DisplayName("When the finding is not sensitive")
  class WhenNotSensitive {

    @Test
    @DisplayName("Should return the value untouched")
    void given_aNonSensitiveFinding_should_returnTheValueAsIs() {
      // -------- Act --------
      String redacted = FindingService.redact("admin:Sup3rS3cret", false);

      // -------- Assert --------
      assertThat(redacted).isEqualTo("admin:Sup3rS3cret");
    }
  }

  @Nested
  @DisplayName("When the finding is sensitive")
  class WhenSensitive {

    @Test
    @DisplayName("Should mask a credential value, identity part included")
    void given_aCredentialShapedValue_should_maskItWithAFragment() {
      // -------- Act --------
      String redacted = FindingService.redact("admin:Sup3rS3cret", true);

      // -------- Assert --------
      assertThat(redacted).isEqualTo("ad" + FindingService.MASK);
      assertThat(redacted).doesNotContain("Sup3rS3cret");
    }

    @Test
    @DisplayName("Should mask a password policy value the same way")
    void given_aPasswordPolicyShapedValue_should_maskItWithAFragment() {
      // -------- Act --------
      String redacted = FindingService.redact("MinimumPasswordLength: 8", true);

      // -------- Assert --------
      assertThat(redacted).isEqualTo("Mi" + FindingService.MASK);
    }

    @Test
    @DisplayName("Should keep a two character fragment of a value without separator")
    void given_aValueWithoutSeparator_should_keepAFragment() {
      // -------- Act --------
      String redacted = FindingService.redact("Sup3rS3cret", true);

      // -------- Assert --------
      assertThat(redacted).isEqualTo("Su" + FindingService.MASK);
    }

    @Test
    @DisplayName("Should mask a short value entirely")
    void given_aShortValue_should_maskItEntirely() {
      // -------- Act --------
      String redacted = FindingService.redact("abcd", true);

      // -------- Assert --------
      assertThat(redacted).isEqualTo(FindingService.MASK);
    }

    @Test
    @DisplayName("Should return blank and null values as is")
    void given_aBlankValue_should_returnItAsIs() {
      // -------- Act & Assert --------
      assertThat(FindingService.redact(null, true)).isNull();
      assertThat(FindingService.redact("  ", true)).isEqualTo("  ");
    }
  }
}

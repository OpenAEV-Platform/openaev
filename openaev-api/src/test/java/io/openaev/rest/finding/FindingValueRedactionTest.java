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
    @DisplayName("Should mask each part of a credential value")
    void given_aCredentialShapedValue_should_maskEveryPart() {
      // -------- Act --------
      String redacted = FindingService.redact("admin:motdepasse", true);

      // -------- Assert --------
      assertThat(redacted).isEqualTo("ad" + FindingService.MASK + ":mo" + FindingService.MASK);
      assertThat(redacted).doesNotContain("motdepasse");
    }

    @Test
    @DisplayName("Should mask every part of a value holding several separators")
    void given_aValueWithSeveralSeparators_should_maskEveryPart() {
      // -------- Act --------
      String redacted = FindingService.redact("admin:aad3b435:31d6cfe0", true);

      // -------- Assert --------
      assertThat(redacted)
          .isEqualTo(
              "ad"
                  + FindingService.MASK
                  + ":aa"
                  + FindingService.MASK
                  + ":31"
                  + FindingService.MASK);
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
    @DisplayName("Should mask a short part entirely")
    void given_aShortPart_should_maskItEntirely() {
      // -------- Act & Assert --------
      assertThat(FindingService.redact("abcd", true)).isEqualTo(FindingService.MASK);
      assertThat(FindingService.redact("MinimumPasswordLength: 8", true))
          .isEqualTo("Mi" + FindingService.MASK + ":" + FindingService.MASK);
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

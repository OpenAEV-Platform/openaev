package io.openaev.telemetry;

import static io.openaev.telemetry.OpenTelemetryConfig.normalizeTelemetryTags;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenTelemetryConfigTest {

  @Test
  @DisplayName("Telemetry tags normalization returns empty string when no tags are configured")
  void shouldReturnEmptyStringWhenNoTagsConfigured() {
    assertThat(normalizeTelemetryTags(null)).isEmpty();
    assertThat(normalizeTelemetryTags("")).isEmpty();
    assertThat(normalizeTelemetryTags("   ")).isEmpty();
    assertThat(normalizeTelemetryTags(" , ,, ")).isEmpty();
  }

  @Test
  @DisplayName("Telemetry tags normalization trims, lowercases, dedupes and sorts tags")
  void shouldNormalizeTagsIntoCanonicalString() {
    assertThat(normalizeTelemetryTags("saas")).isEqualTo("saas");
    assertThat(normalizeTelemetryTags("saas,eu-west")).isEqualTo("eu-west,saas");
    assertThat(normalizeTelemetryTags("  EU-West ,SAAS, saas,, ")).isEqualTo("eu-west,saas");
    assertThat(normalizeTelemetryTags("b,a,c,a")).isEqualTo("a,b,c");
  }
}

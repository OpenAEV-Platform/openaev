package io.openaev.utils;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.stix.objects.DomainObject;
import io.openaev.stix.objects.constants.CommonProperties;
import io.openaev.stix.types.BaseType;
import io.openaev.stix.types.StixString;
import io.openaev.stix.types.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** #9875: false-positive indicators of a coverage bundle never become injects. */
@DisplayName("Security coverage indicator filter")
class SecurityCoverageIndicatorFilterTest {

  private static final Instant NOW = Instant.parse("2026-11-26T08:00:00Z");

  private static DomainObject indicator(Map<String, BaseType<?>> properties) {
    Map<String, BaseType<?>> all = new HashMap<>(properties);
    all.put(CommonProperties.TYPE.toString(), new StixString("indicator"));
    return new DomainObject(all);
  }

  @Test
  @DisplayName("given_plainIndicator_should_beUsable")
  void given_plainIndicator_should_beUsable() {
    assertThat(SecurityCoverageUtils.isUsableIndicator(indicator(Map.of()), NOW)).isTrue();
  }

  @Test
  @DisplayName("given_falsePositiveLabel_should_beSkipped")
  void given_falsePositiveLabel_should_beSkipped() {
    DomainObject fp =
        indicator(
            Map.of(
                CommonProperties.LABELS.toString(),
                new io.openaev.stix.types.List<>(
                    List.of(new StixString("phishing"), new StixString("False-Positive")))));
    assertThat(SecurityCoverageUtils.isUsableIndicator(fp, NOW)).isFalse();
  }

  @Test
  @DisplayName("given_notForDetection_should_beSkipped")
  void given_notForDetection_should_beSkipped() {
    DomainObject noDetection =
        indicator(
            Map.of(
                SecurityCoverageUtils.STIX_OPENCTI_DETECTION,
                new io.openaev.stix.types.Boolean(false)));
    assertThat(SecurityCoverageUtils.isUsableIndicator(noDetection, NOW)).isFalse();
  }

  @Test
  @DisplayName("given_revokedOrExpired_should_beSkipped")
  void given_revokedOrExpired_should_beSkipped() {
    DomainObject revoked =
        indicator(
            Map.of(SecurityCoverageUtils.STIX_REVOKED, new io.openaev.stix.types.Boolean(true)));
    DomainObject expired =
        indicator(
            Map.of(
                SecurityCoverageUtils.STIX_VALID_UNTIL,
                new Timestamp(Instant.parse("2026-01-01T00:00:00Z"))));
    DomainObject valid =
        indicator(
            Map.of(
                SecurityCoverageUtils.STIX_VALID_UNTIL,
                new Timestamp(Instant.parse("2027-01-01T00:00:00Z"))));
    assertThat(SecurityCoverageUtils.isUsableIndicator(revoked, NOW)).isFalse();
    assertThat(SecurityCoverageUtils.isUsableIndicator(expired, NOW)).isFalse();
    assertThat(SecurityCoverageUtils.isUsableIndicator(valid, NOW)).isTrue();
  }
}

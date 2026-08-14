package io.openaev.service.finding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.FindingSeverityBucket;
import io.openaev.database.model.RiskScoreBucket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RiskScoreServiceTest {

  private final RiskScoreService service = new RiskScoreService();

  @Nested
  @DisplayName("High-severity findings never degrade below HIGH")
  class HighSeverityFloor {

    @Test
    @DisplayName("Should be CRITICAL for CRITICAL severity on a VERY_HIGH criticality asset")
    void given_critical_severity_very_high_criticality_should_be_critical() {
      assertEquals(
          RiskScoreBucket.CRITICAL,
          service.computeRiskScore(FindingSeverityBucket.CRITICAL, AssetCriticality.VERY_HIGH));
    }

    @Test
    @DisplayName("Should still be HIGH for CRITICAL severity on a LOW criticality asset")
    void given_critical_severity_low_criticality_should_be_high() {
      assertEquals(
          RiskScoreBucket.HIGH,
          service.computeRiskScore(FindingSeverityBucket.CRITICAL, AssetCriticality.LOW));
    }

    @Test
    @DisplayName("Should still be HIGH for CRITICAL severity on an UNKNOWN criticality asset")
    void given_critical_severity_unknown_criticality_should_be_high() {
      assertEquals(
          RiskScoreBucket.HIGH,
          service.computeRiskScore(FindingSeverityBucket.CRITICAL, AssetCriticality.UNKNOWN));
    }

    @Test
    @DisplayName("Should still be HIGH for HIGH severity on an UNKNOWN criticality asset")
    void given_high_severity_unknown_criticality_should_be_high() {
      assertEquals(
          RiskScoreBucket.HIGH,
          service.computeRiskScore(FindingSeverityBucket.HIGH, AssetCriticality.UNKNOWN));
    }
  }

  @Nested
  @DisplayName("NOT_ENOUGH_DATA only at the UNKNOWN x UNKNOWN corner")
  class NotEnoughDataCorner {

    @Test
    @DisplayName("Should be NOT_ENOUGH_DATA for UNKNOWN severity and UNKNOWN criticality")
    void given_unknown_severity_unknown_criticality_should_be_not_enough_data() {
      assertEquals(
          RiskScoreBucket.NOT_ENOUGH_DATA,
          service.computeRiskScore(FindingSeverityBucket.UNKNOWN, AssetCriticality.UNKNOWN));
    }

    @Test
    @DisplayName("Should NOT be NOT_ENOUGH_DATA when only severity is UNKNOWN")
    void given_unknown_severity_known_criticality_should_not_be_not_enough_data() {
      assertEquals(
          RiskScoreBucket.MEDIUM,
          service.computeRiskScore(FindingSeverityBucket.UNKNOWN, AssetCriticality.VERY_HIGH));
    }

    @Test
    @DisplayName("Should NOT be NOT_ENOUGH_DATA when only criticality is UNKNOWN")
    void given_known_severity_unknown_criticality_should_not_be_not_enough_data() {
      assertEquals(
          RiskScoreBucket.LOW,
          service.computeRiskScore(FindingSeverityBucket.LOW, AssetCriticality.UNKNOWN));
    }
  }

  @Nested
  @DisplayName("isEstimated flag")
  class EstimatedFlag {

    @Test
    @DisplayName("Should be estimated when severity is UNKNOWN")
    void given_unknown_severity_should_be_estimated() {
      assertTrue(service.isEstimated(FindingSeverityBucket.UNKNOWN, AssetCriticality.HIGH));
    }

    @Test
    @DisplayName("Should be estimated when criticality is UNKNOWN")
    void given_unknown_criticality_should_be_estimated() {
      assertTrue(service.isEstimated(FindingSeverityBucket.HIGH, AssetCriticality.UNKNOWN));
    }

    @Test
    @DisplayName("Should not be estimated when both axes are fully known")
    void given_both_axes_known_should_not_be_estimated() {
      assertFalse(service.isEstimated(FindingSeverityBucket.HIGH, AssetCriticality.HIGH));
    }
  }

  @Nested
  @DisplayName("Mid-matrix scaling")
  class MidMatrixScaling {

    @Test
    @DisplayName("Should be MEDIUM for MEDIUM severity on a MEDIUM criticality asset")
    void given_medium_severity_medium_criticality_should_be_medium() {
      assertEquals(
          RiskScoreBucket.MEDIUM,
          service.computeRiskScore(FindingSeverityBucket.MEDIUM, AssetCriticality.MEDIUM));
    }

    @Test
    @DisplayName("Should be MEDIUM for LOW severity on a VERY_HIGH criticality asset (bumped up)")
    void given_low_severity_very_high_criticality_should_be_bumped_to_medium() {
      assertEquals(
          RiskScoreBucket.MEDIUM,
          service.computeRiskScore(FindingSeverityBucket.LOW, AssetCriticality.VERY_HIGH));
    }
  }
}

package io.openaev.service;

import static io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR;
import static io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE.SIEM;
import static io.openaev.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE.XDR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.database.model.Collector;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.model.SecurityPlatform;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InjectExpectationServiceSecurityPlatformTest {

  private Collector collectorOfType(
      String id, int period, SecurityPlatform.SECURITY_PLATFORM_TYPE type) {
    Collector collector = new Collector();
    collector.setId(id);
    collector.setName(id);
    collector.setPeriod(period);
    if (type != null) {
      SecurityPlatform platform = new SecurityPlatform();
      platform.setSecurityPlatformType(type);
      collector.setSecurityPlatform(platform);
    }
    return collector;
  }

  @Test
  @DisplayName("Empty expected types keeps every connected collector (legacy behaviour)")
  void emptyExpectedTypesKeepsAllCollectors() {
    List<Collector> collectors =
        List.of(
            collectorOfType("edr", 60, EDR),
            collectorOfType("siem", 60, SIEM),
            collectorOfType("xdr", 60, XDR));

    List<Collector> result =
        InjectExpectationUtils.filterCollectorsForExpectation(collectors, List.of());

    assertEquals(3, result.size());
  }

  @Test
  @DisplayName("Non-empty expected types keeps only collectors of those platform types")
  void nonEmptyExpectedTypesFiltersCollectors() {
    List<Collector> collectors =
        List.of(
            collectorOfType("edr", 60, EDR),
            collectorOfType("siem", 60, SIEM),
            collectorOfType("xdr", 60, XDR));

    List<Collector> result =
        InjectExpectationUtils.filterCollectorsForExpectation(collectors, List.of(EDR, XDR));

    assertEquals(2, result.size());
    assertTrue(result.stream().allMatch(c -> List.of("edr", "xdr").contains(c.getId())));
  }

  @Test
  @DisplayName("Expiration floor is at least two collector poll cycles when platforms are expected")
  void expirationFloorRespectsCollectorPeriod() {
    DetectionInjectExpectation expectation = new DetectionInjectExpectation();
    expectation.setExpirationTime(30L); // shorter than the expected collector latency

    InjectExpectationUtils.applyExpirationOrderingGuarantee(
        expectation, List.of(collectorOfType("edr", 300, EDR)));

    // floor = maxPeriod (300s) * 2 = 600s, greater than the declared 30s
    assertEquals(600L, expectation.getExpirationTime());
  }

  @Test
  @DisplayName("Expiration is left untouched when it already exceeds the collector floor")
  void expirationKeptWhenAboveFloor() {
    DetectionInjectExpectation expectation = new DetectionInjectExpectation();
    expectation.setExpirationTime(21600L);

    InjectExpectationUtils.applyExpirationOrderingGuarantee(
        expectation, List.of(collectorOfType("edr", 60, EDR)));

    assertEquals(21600L, expectation.getExpirationTime());
  }

  @Test
  @DisplayName("No expected collectors leaves expiration unchanged")
  void noExpectedCollectorsLeavesExpirationUnchanged() {
    DetectionInjectExpectation expectation = new DetectionInjectExpectation();
    expectation.setExpirationTime(120L);

    InjectExpectationUtils.applyExpirationOrderingGuarantee(expectation, List.of());

    assertEquals(120L, expectation.getExpirationTime());
  }
}

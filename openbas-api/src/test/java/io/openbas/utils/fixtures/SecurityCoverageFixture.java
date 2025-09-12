package io.openbas.utils.fixtures;

import io.openbas.cron.ScheduleFrequency;
import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.SecurityCoverage;
import io.openbas.database.model.StixRefToExternalRef;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SecurityCoverageFixture {
  public static SecurityCoverage createDefaultSecurityCoverage() {
    SecurityCoverage securityCoverage = new SecurityCoverage();
    securityCoverage.setName("Security assessment for tests");
    securityCoverage.setExternalId("x-security-assessment--%s".formatted(UUID.randomUUID()));
    securityCoverage.setScheduling(ScheduleFrequency.DAILY);
    securityCoverage.setContent(
        "{\"type\": \"x-security-assessment\", \"id\": \"%s\"}"
            .formatted(securityCoverage.getExternalId()));
    return securityCoverage;
  }

  public static SecurityCoverage createSecurityCoverageWithAttackPatterns(
      List<AttackPattern> attackPatterns) {
    SecurityCoverage securityCoverage = createDefaultSecurityCoverage();
    securityCoverage.setAttackPatternRefs(
        attackPatterns.stream()
            .map(
                ap ->
                    new StixRefToExternalRef(
                        "attack-pattern--%s".formatted(ap.getId()), ap.getExternalId()))
            .collect(Collectors.toSet()));
    return securityCoverage;
  }
}

package io.openbas.utils.fixtures;

import io.openbas.cron.ScheduleFrequency;
import io.openbas.database.model.AttackPattern;
import io.openbas.database.model.Cve;
import io.openbas.database.model.SecurityCoverage;
import io.openbas.database.model.StixRefToExternalRef;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    securityCoverage.setAttackPatternRefs(new HashSet<>());
    securityCoverage.setVulnerabilitiesRefs(new HashSet<>());
    return securityCoverage;
  }

  public static SecurityCoverage createSecurityCoverageWithDomainObjects(
      List<AttackPattern> attackPatterns, List<Cve> vulnerabilities) {
    Set<StixRefToExternalRef> attackPatternRefs =
        attackPatterns.stream()
            .map(
                ap ->
                    new StixRefToExternalRef(
                        "attack-pattern--%s".formatted(ap.getId()), ap.getExternalId()))
            .collect(Collectors.toSet());
    Set<StixRefToExternalRef> vulnerabilitiesRefs =
        vulnerabilities.stream()
            .map(
                ap ->
                    new StixRefToExternalRef(
                        "vulnerability--%s".formatted(ap.getId()), ap.getExternalId()))
            .collect(Collectors.toSet());

    SecurityCoverage securityCoverage = createDefaultSecurityCoverage();
    securityCoverage.setAttackPatternRefs(attackPatternRefs);
    securityCoverage.setVulnerabilitiesRefs(vulnerabilitiesRefs);

    return securityCoverage;
  }
}

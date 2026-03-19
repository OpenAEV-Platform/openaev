package io.openaev.utils.fixtures;

import static io.openaev.helper.CryptoHelper.md5Hex;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.database.model.StixRefToExternalRef;
import io.openaev.database.model.Vulnerability;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SecurityCoverageFixture {
  public static SecurityCoverage createDefaultSecurityCoverage() {
    SecurityCoverage securityCoverage = new SecurityCoverage();
    securityCoverage.setName("Security coverage for tests");
    securityCoverage.setExternalId("security-coverage--%s".formatted(UUID.randomUUID()));
    securityCoverage.setScheduling("PT1H");
    securityCoverage.setContent(
        "{\"type\": \"security-coverage\", \"id\": \"%s\"}"
            .formatted(securityCoverage.getExternalId()));
    securityCoverage.setAttackPatternRefs(new HashSet<>());
    securityCoverage.setVulnerabilitiesRefs(new HashSet<>());
    securityCoverage.setIndicatorsRefs(new HashSet<>());
    securityCoverage.setBundleHashMd5(md5Hex(securityCoverage.getContent()));
    return securityCoverage;
  }

  public static SecurityCoverage createSecurityCoverageWithDomainObjects(
      List<AttackPattern> attackPatterns, List<Vulnerability> vulnerabilities) {
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
    securityCoverage.setBundleHashMd5(md5Hex(UUID.randomUUID().toString()));

    return securityCoverage;
  }
}

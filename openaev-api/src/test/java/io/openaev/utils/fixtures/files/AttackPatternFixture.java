package io.openaev.utils.fixtures.files;

import io.openaev.database.model.AttackPattern;
import io.openaev.database.model.Tenant;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;

public class AttackPatternFixture {

  /**
   * {@code attack_patterns} is v2-active and carries no tenant listener any more, so a fixture
   * saved straight through the repository must name the tenant it belongs to.
   */
  public static AttackPattern createDefaultAttackPattern(String tenantId) {
    return inTenant(createDefaultAttackPattern(), tenantId);
  }

  public static AttackPattern createAttackPatternsWithExternalId(
      final String externalId, String tenantId) {
    return inTenant(createAttackPatternsWithExternalId(externalId), tenantId);
  }

  private static AttackPattern inTenant(AttackPattern attackPattern, String tenantId) {
    attackPattern.setTenant(new Tenant(tenantId));
    return attackPattern;
  }

  public static AttackPattern createDefaultAttackPattern() {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setName("AttackPattern-" + RandomStringUtils.random(25, true, true));
    attackPattern.setExternalId(
        "T"
            + RandomStringUtils.random(4, false, true)
            + "."
            + RandomStringUtils.random(3, false, true));
    attackPattern.setStixId("attack-pattern-test--" + UUID.randomUUID());
    return attackPattern;
  }

  public static AttackPattern createAttackPatternsWithExternalId(final String externalId) {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setName("AttackPattern-" + externalId);
    attackPattern.setExternalId(externalId);
    attackPattern.setStixId("attack-pattern-test--" + externalId);
    return attackPattern;
  }
}

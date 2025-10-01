package io.openaev.utils.fixtures.files;

import io.openaev.database.model.AttackPattern;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;

public class AttackPatternFixture {

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

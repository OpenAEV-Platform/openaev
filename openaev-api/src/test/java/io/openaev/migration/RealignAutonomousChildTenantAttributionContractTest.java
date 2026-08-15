package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the Flyway contract that makes the autonomous child-row tenant repair transition-safe for
 * rolling deployments: the migration is REPEATABLE (no version - instantiating it also proves the
 * {@code R__} class name parses) and its checksum is non-null and stable within one JVM. Flyway
 * re-applies a repeatable migration whenever its checksum differs from the recorded one; the
 * per-boot snapshot makes every later boot resolve a new value, so a default-stamped child written
 * by a not-yet-upgraded node AFTER an earlier repair is realigned at the latest on the next boot.
 * The cross-boot difference itself cannot be asserted from inside a single JVM - the in-JVM
 * stability half is what guarantees Flyway compares and records one consistent value per boot.
 */
@DisplayName("Realign autonomous child tenant attribution - repeatable migration contract")
class RealignAutonomousChildTenantAttributionContractTest {

  @Test
  @DisplayName("the migration is repeatable (versionless), so Flyway can re-apply it")
  void migrationIsRepeatable() {
    assertThat(new R__Realign_autonomous_child_tenant_attribution().getVersion()).isNull();
  }

  @Test
  @DisplayName("the checksum is non-null and stable within the JVM (one value per boot)")
  void checksumIsStableWithinOneBoot() {
    R__Realign_autonomous_child_tenant_attribution first =
        new R__Realign_autonomous_child_tenant_attribution();
    R__Realign_autonomous_child_tenant_attribution second =
        new R__Realign_autonomous_child_tenant_attribution();

    assertThat(first.getChecksum()).isNotNull();
    assertThat(first.getChecksum()).isEqualTo(first.getChecksum());
    assertThat(first.getChecksum()).isEqualTo(second.getChecksum());
  }
}

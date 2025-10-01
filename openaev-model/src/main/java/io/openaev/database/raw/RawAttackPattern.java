package io.openaev.database.raw;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
public interface RawAttackPattern {
  String getAttack_pattern_id();

  String getAttack_pattern_stix_id();

  String getAttack_pattern_name();

  String getAttack_pattern_description();

  String getAttack_pattern_external_id();

  List<String> getAttack_pattern_platforms();

  List<String> getAttack_pattern_permissions_required();

  Instant getAttack_pattern_created_at();

  Instant getAttack_pattern_updated_at();

  String getAttack_pattern_parent();

  Set<String> getAttack_pattern_kill_chain_phases();
}

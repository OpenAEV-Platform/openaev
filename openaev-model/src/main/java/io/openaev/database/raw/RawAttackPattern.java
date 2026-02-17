package io.openaev.database.raw;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Spring Data projection interface for MITRE ATT&CK pattern data.
 *
 * <p>This interface defines a projection for retrieving attack pattern information from the MITRE
 * ATT&CK framework, including technique identifiers, platform applicability, and kill chain phase
 * mappings.
 *
 * @see io.openaev.database.model.AttackPattern
 */
@SuppressWarnings("unused")
public interface RawAttackPattern {

  /**
   * Returns the unique identifier of the attack pattern.
   *
   * @return the attack pattern ID
   */
  String getAttack_pattern_id();

  /**
   * Returns the STIX 2.x identifier for this attack pattern.
   *
   * @return the STIX ID (e.g., "attack-pattern--...")
   */
  String getAttack_pattern_stix_id();

  /**
   * Returns the display name of the attack pattern.
   *
   * @return the attack pattern name (e.g., "PowerShell")
   */
  String getAttack_pattern_name();

  /**
   * Returns the detailed description of the attack pattern.
   *
   * @return the attack pattern description
   */
  String getAttack_pattern_description();

  /**
   * Returns the MITRE ATT&CK external ID.
   *
   * @return the external ID (e.g., "T1059.001" for PowerShell)
   */
  String getAttack_pattern_external_id();

  /**
   * Returns the list of platforms this attack pattern applies to.
   *
   * @return list of platforms (e.g., ["Windows", "Linux", "macOS"])
   */
  List<String> getAttack_pattern_platforms();

  /**
   * Returns the list of permissions required to execute this attack pattern.
   *
   * @return list of required permissions (e.g., ["User", "Administrator"])
   */
  List<String> getAttack_pattern_permissions_required();

  /**
   * Returns the creation timestamp of the attack pattern record.
   *
   * @return the creation timestamp
   */
  Instant getAttack_pattern_created_at();

  /**
   * Returns the last update timestamp of the attack pattern record.
   *
   * @return the update timestamp
   */
  Instant getAttack_pattern_updated_at();

  /**
   * Returns the ID of the parent attack pattern (for sub-techniques).
   *
   * @return the parent attack pattern ID, or {@code null} for top-level techniques
   */
  String getAttack_pattern_parent();

  /**
   * Returns the set of kill chain phase IDs associated with this attack pattern.
   *
   * @return set of kill chain phase IDs
   */
  Set<String> getAttack_pattern_kill_chain_phases();
}

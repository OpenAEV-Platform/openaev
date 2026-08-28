package io.openaev.rest.kill_chain_phase;

import java.util.HashMap;
import java.util.Map;

public final class KillChainPhaseUtils {

  public static final String MITRE_ATTACK_KILL_CHAIN_NAME = "mitre-attack";
  public static final String MITRE_ATLAS_KILL_CHAIN_NAME = "mitre-atlas";

  private KillChainPhaseUtils() {}

  public static Map<String, Long> orderFromMitreAttack() {
    Map<String, Long> map = new HashMap<>();
    map.put("reconnaissance", 0L);
    map.put("resource-development", 1L);
    map.put("initial-access", 2L);
    map.put("execution", 3L);
    map.put("persistence", 4L);
    map.put("privilege-escalation", 5L);
    map.put("defense-evasion", 6L);
    map.put("credential-access", 7L);
    map.put("discovery", 8L);
    map.put("lateral-movement", 9L);
    map.put("collection", 10L);
    map.put("command-and-control", 11L);
    map.put("exfiltration", 12L);
    map.put("impact", 13L);
    return map;
  }

  /**
   * Tactic order for the MITRE ATLAS matrix (Adversarial Threat Landscape for AI Systems). Keyed by
   * the tactic short name (lower-cased, hyphenated). Mirrors the ATLAS matrix sequence so the
   * coverage matrix renders ATLAS tactics in the canonical left-to-right order.
   */
  public static Map<String, Long> orderFromMitreAtlas() {
    Map<String, Long> map = new HashMap<>();
    map.put("reconnaissance", 0L);
    map.put("resource-development", 1L);
    map.put("initial-access", 2L);
    map.put("ai-model-access", 3L);
    map.put("execution", 4L);
    map.put("persistence", 5L);
    map.put("privilege-escalation", 6L);
    map.put("defense-evasion", 7L);
    map.put("credential-access", 8L);
    map.put("discovery", 9L);
    map.put("lateral-movement", 10L);
    map.put("collection", 11L);
    map.put("ai-attack-staging", 12L);
    map.put("command-and-control", 13L);
    map.put("exfiltration", 14L);
    map.put("impact", 15L);
    return map;
  }

  /**
   * Resolves the canonical order for a kill chain phase from its kill chain name and short name,
   * falling back to {@code 0L} when the short name is unknown for the given kill chain.
   */
  public static Long orderFor(String killChainName, String shortName) {
    Map<String, Long> map =
        MITRE_ATLAS_KILL_CHAIN_NAME.equalsIgnoreCase(killChainName)
            ? orderFromMitreAtlas()
            : orderFromMitreAttack();
    return map.getOrDefault(shortName, 0L);
  }
}

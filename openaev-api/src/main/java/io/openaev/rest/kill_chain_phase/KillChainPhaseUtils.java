package io.openaev.rest.kill_chain_phase;

import java.util.HashMap;
import java.util.Map;

public final class KillChainPhaseUtils {

  private KillChainPhaseUtils() {}

  public static Map<String, Long> orderFromMitreAttack() {
    Map<String, Long> map = new HashMap<>();
    map.put("credential-access", 7L);
    map.put("execution", 3L);
    map.put("impact", 13L);
    map.put("persistence", 4L);
    map.put("privilege-escalation", 5L);
    map.put("lateral-movement", 9L);
    map.put("defense-evasion", 6L);
    map.put("exfiltration", 12L);
    map.put("discovery", 8L);
    map.put("collection", 10L);
    map.put("resource-development", 1L);
    map.put("reconnaissance", 0L);
    map.put("command-and-control", 11L);
    map.put("initial-access", 2L);
    return map;
  }
}

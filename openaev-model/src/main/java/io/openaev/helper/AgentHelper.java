package io.openaev.helper;

import static java.time.Instant.now;

import java.time.Instant;

public class AgentHelper {

  public static final int ACTIVE_THRESHOLD = 3600000; // 3 600 000 ms = 1 hour

  public boolean isAgentActiveFromLastSeen(Instant lastSeen) {
    return lastSeen != null && (now().toEpochMilli() - lastSeen.toEpochMilli()) < ACTIVE_THRESHOLD;
  }
}

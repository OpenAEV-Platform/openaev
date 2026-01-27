package io.openaev.helper;

import static java.time.Instant.now;

import java.time.Instant;

/**
 * Helper class for agent-related operations and status calculations.
 *
 * <p>This class provides utility methods for determining agent connectivity status based on their
 * last communication timestamp.
 *
 * @see io.openaev.database.model.Agent
 */
public class AgentHelper {

  /** Threshold in milliseconds to consider an agent as active (1 hour = 3,600,000 ms). */
  public static final int ACTIVE_THRESHOLD = 3600000;

  /**
   * Determines if an agent is currently active based on its last seen timestamp.
   *
   * <p>An agent is considered active if it has communicated with the platform within the last hour
   * (defined by {@link #ACTIVE_THRESHOLD}).
   *
   * @param lastSeen the timestamp of the agent's last communication, or {@code null} if never seen
   * @return {@code true} if the agent is considered active, {@code false} otherwise
   */
  public boolean isAgentActiveFromLastSeen(Instant lastSeen) {
    return lastSeen != null && (now().toEpochMilli() - lastSeen.toEpochMilli()) < ACTIVE_THRESHOLD;
  }
}

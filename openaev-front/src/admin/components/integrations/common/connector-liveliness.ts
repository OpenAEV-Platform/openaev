import { type ConnectorOutput } from './ConnectorContext';

/** An external connector that has not pinged for longer than this is considered down. */
export const LIVELINESS_THRESHOLD_MS = 2 * 60 * 1000;

export interface ConnectorLiveliness {
  started: boolean;
  /** Coherent health signal driving the status disk color (green = healthy, red = not). */
  healthy: boolean;
  /**
   * Relative "last seen" timestamp shown next to the disk. Populated whenever the
   * connector is actually alive: the real heartbeat for a running external
   * connector, or "now" for an in-process built-in (it runs inside this
   * platform process, so it is live by definition). Stopped / dead connectors
   * carry no date - a fresh timestamp next to a Stopped chip is misleading.
   */
  lastSeen?: string;
  /** In-process connector shipped with the platform (no external heartbeat). */
  builtIn: boolean;
}

export const isHeartbeatFresh = (heartbeat?: string): boolean =>
  heartbeat != null && Date.now() - new Date(heartbeat).getTime() < LIVELINESS_THRESHOLD_MS;

/**
 * Uniform status resolution for every deployed connector.
 *
 * The `updatedAt` field is only a genuine heartbeat for running external
 * connectors (they re-register every ~40s). It is unreliable elsewhere: a
 * registration date for built-ins, and a sync bump for stopped instances - so
 * it must never be surfaced as "last seen" outside the living cases below.
 *
 * - built-in, in-process connectors (`!external && existing`): always running,
 *   green, last seen = now (they execute inside this platform process);
 * - deployed instances: the integration manager's Started/Stopped status wins.
 *   A stopped instance shows no date (red Stopped chip only); a started one
 *   shows the real heartbeat when external, or "now" when built-in;
 * - legacy external connectors without an instance: alive iff they pinged
 *   within the threshold (fresh heartbeat shown), otherwise stopped, no date;
 * - anything else (non-deployed catalog placeholder): stopped, no date.
 */
export const computeConnectorLiveliness = (connector: ConnectorOutput): ConnectorLiveliness => {
  const heartbeat = connector.updatedAt;
  const external = connector.isExternal === true;
  const nowIso = new Date().toISOString();

  if (!external && connector.isExisting) {
    return {
      started: true,
      healthy: true,
      lastSeen: nowIso,
      builtIn: true,
    };
  }

  if (connector.connectorInstance != null) {
    const started = connector.connectorInstance.connector_instance_current_status === 'started';
    if (!started) {
      return {
        started: false,
        healthy: false,
        builtIn: false,
      };
    }
    if (external) {
      const live = isHeartbeatFresh(heartbeat);
      return {
        started: true,
        healthy: live,
        lastSeen: heartbeat,
        builtIn: false,
      };
    }
    // Built-in instance (e.g. Manual / Challenges injectors): in-process, live.
    return {
      started: true,
      healthy: true,
      lastSeen: nowIso,
      builtIn: true,
    };
  }

  if (external) {
    const live = isHeartbeatFresh(heartbeat);
    return {
      started: live,
      healthy: live,
      lastSeen: live ? heartbeat : undefined,
      builtIn: false,
    };
  }

  // Non-external, non-existing, no instance: nothing actually deployed.
  return {
    started: false,
    healthy: false,
    builtIn: false,
  };
};

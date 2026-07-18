import { type ConnectorOutput } from './ConnectorContext';

/** An external connector that has not pinged for longer than this is considered down. */
export const LIVELINESS_THRESHOLD_MS = 2 * 60 * 1000;

export interface ConnectorLiveliness {
  started: boolean;
  /** Coherent health signal driving the status disk color (green = healthy, red = not). */
  healthy: boolean;
  /**
   * Relative "last seen" timestamp, only populated when it is a genuine
   * heartbeat worth showing (external connectors that re-register while
   * running). Built-in / in-process connectors and non-deployed placeholders
   * leave this undefined so no misleading date is rendered.
   */
  lastSeen?: string;
  /** In-process connector shipped with the platform: always running, no external heartbeat. */
  builtIn: boolean;
}

/**
 * Uniform status resolution for every deployed connector.
 *
 * The `updatedAt` timestamp is only a real heartbeat for external connectors
 * (they re-register every ~40s while running). For built-in connectors it is a
 * registration date, and for non-deployed catalog placeholders it is a sync
 * bump - showing either as "last seen" is misleading, so those cases return no
 * `lastSeen` and rely on the Started/Stopped chip plus the health disk instead.
 *
 * - built-in, in-process connectors (`!external && existing`): always started
 *   and healthy, no heartbeat date (collectors, injectors, the agent executor);
 * - managed instances: status tracked by the integration manager, with the
 *   heartbeat shown only when the connector is external;
 * - legacy external connectors: alive iff they pinged within the threshold;
 * - anything else (non-deployed catalog placeholder): stopped, no date.
 */
export const computeConnectorLiveliness = (connector: ConnectorOutput): ConnectorLiveliness => {
  const heartbeat = connector.updatedAt;
  const live = heartbeat != null && Date.now() - new Date(heartbeat).getTime() < LIVELINESS_THRESHOLD_MS;
  const stale = heartbeat != null && !live;
  const external = connector.isExternal === true;

  // Built-in connectors run inside the platform process: they are always on and
  // have no external heartbeat, so their registration/last-execution date must
  // never be surfaced as a stale "last seen".
  if (!external && connector.isExisting) {
    return {
      started: true,
      healthy: true,
      builtIn: true,
    };
  }

  if (connector.connectorInstance != null) {
    const started = connector.connectorInstance.connector_instance_current_status === 'started';
    // Only external instances report a real heartbeat; a stale one means the
    // instance silently died.
    const externallyDead = external && stale;
    return {
      started,
      healthy: started && !externallyDead,
      lastSeen: external ? heartbeat : undefined,
      builtIn: false,
    };
  }

  if (external) {
    return {
      started: live,
      healthy: live,
      lastSeen: heartbeat,
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

import { type ConnectorOutput } from './ConnectorContext';

/** A connector that has not pinged for longer than this is considered down. */
export const LIVELINESS_THRESHOLD_MS = 2 * 60 * 1000;

export interface ConnectorLiveliness {
  started: boolean;
  lastSeen?: string;
  /** Coherent health signal: started AND no stale heartbeat. Drives the status disk color. */
  healthy: boolean;
}

/**
 * Uniform status resolution for every deployed connector:
 *
 * - managed instances report the status tracked by the integration manager; a
 *   running instance with a heartbeat older than the threshold is unhealthy;
 * - external legacy connectors are alive iff they pinged within the threshold
 *   (external connectors re-register every ~40s while running);
 * - built-in connectors run inside the platform process and are always
 *   started; their last-seen date is informational (last execution).
 */
export const computeConnectorLiveliness = (connector: ConnectorOutput): ConnectorLiveliness => {
  const lastSeen = connector.updatedAt;
  const live = lastSeen != null && Date.now() - new Date(lastSeen).getTime() < LIVELINESS_THRESHOLD_MS;
  const stale = lastSeen != null && !live;
  if (connector.connectorInstance != null) {
    const started = connector.connectorInstance.connector_instance_current_status === 'started';
    // Only external connectors ping while running: a stale heartbeat on a
    // started external instance means it silently died. Built-in instances run
    // inside the platform process, their dates are informational.
    const externallyDead = connector.isExternal === true && stale;
    return {
      started,
      lastSeen,
      healthy: started && !externallyDead,
    };
  }
  if (connector.isExternal) {
    return {
      started: live,
      lastSeen,
      healthy: live,
    };
  }
  return {
    started: true,
    lastSeen,
    healthy: true,
  };
};

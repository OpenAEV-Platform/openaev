import { type ConnectorOutput } from './ConnectorContext';

/** An external connector that has not pinged for longer than this is considered down. */
export const LIVELINESS_THRESHOLD_MS = 2 * 60 * 1000;

const isFresh = (heartbeat?: string): boolean =>
  heartbeat != null && Date.now() - new Date(heartbeat).getTime() < LIVELINESS_THRESHOLD_MS;

export const isConnectorAlive = (connector: ConnectorOutput): boolean => {
  const heartbeat = connector.lastSeen;

  return connector.connectorInstance == null
    ? isFresh(heartbeat)
    : connector.connectorInstance.connector_instance_current_status == 'started';
};

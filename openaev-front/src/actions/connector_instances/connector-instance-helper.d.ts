import type { ConnectorInstance } from '../../utils/api-types';

export interface ConnectorInstanceHelper { getConnectorInstance: (instanceId: string) => ConnectorInstance }

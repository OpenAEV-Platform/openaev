import type { ConnectorInstanceOutput } from '../../utils/api-types';

export interface ConnectorInstanceHelper { getConnectorInstance: (instanceId: string) => ConnectorInstanceOutput }

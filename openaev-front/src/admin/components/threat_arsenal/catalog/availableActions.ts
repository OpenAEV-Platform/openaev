import { useFormatter } from '../../../../components/i18n';
import {
  type AvailableActionOutput,
  type AvailableIntegrationActionOutput,
  type AvailablePayloadActionOutput,
} from '../../../../utils/api-types';

interface ByKind<R> {
  PAYLOAD: (action: AvailablePayloadActionOutput) => R;
  INTEGRATION: (action: AvailableIntegrationActionOutput) => R;
}

export const byKind = <R>(handlers: ByKind<R>) => (action: AvailableActionOutput): R => (
  action.action_kind === 'PAYLOAD' ? handlers.PAYLOAD(action) : handlers.INTEGRATION(action)
);

type ActionName = (action: Pick<AvailableActionOutput, 'action_id' | 'action_labels'>) => string;

export const useActionName = (): ActionName => {
  const { tPick } = useFormatter();
  return action => tPick(action.action_labels) || Object.values(action.action_labels)[0] || action.action_id;
};

export const integrationUrl = (action: AvailableIntegrationActionOutput) => `/admin/integrations/catalog/${action.action_integration_id}`;
